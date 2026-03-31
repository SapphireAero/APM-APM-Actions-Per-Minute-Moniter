package com.apm.monitor.core.engine;

import com.apm.monitor.core.window.WindowService;
import com.apm.monitor.model.ApmPoint;
import com.apm.monitor.model.ApmSnapshot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * APM 统计核心引擎。
 * <p>
 * 设计目标：
 * 1) 实时接收输入事件（{@link #onInputEvent()}）；
 * 2) 每秒聚合一次并计算 APM（{@link #onTick()}）；
 * 3) 按固定快照模型向上层发布状态（{@link ApmSnapshot}）。
 * <p>
 * 线程模型：
 * - 输入事件线程：来自全局钩子回调；
 * - 统计线程：单线程定时任务；
 * - UI 线程：消费快照回调（由调用方自行切线程）。
 */
public class ApmMonitorEngine implements AutoCloseable {
    private static final int MAX_POINTS = 86_400;
    private static final Locale LOCALE = Locale.ROOT;

    private final WindowService windowService;
    private final Consumer<ApmSnapshot> tickCallback;
    /**
     * 单线程调度器，保证 tick 顺序执行，避免并发聚合冲突。
     */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new TickThreadFactory());
    /**
     * 当前秒内累计输入事件数。
     */
    private final AtomicLong eventsInSecond = new AtomicLong(0);
    private final AtomicBoolean monitoring = new AtomicBoolean(false);

    /**
     * 历史点序列。只在 pointLock 保护下读写。
     */
    private final List<ApmPoint> points = new ArrayList<>();
    /**
     * 保护会话计时相关状态。
     */
    private final Object stateLock = new Object();
    /**
     * 保护统计点集合。
     */
    private final Object pointLock = new Object();

    private volatile String lastActiveWindow = "未知窗口";
    private volatile String targetWindowTitle = "";
    private volatile boolean filterByWindow = false;
    private volatile long sessionStartedAtMillis = 0L;
    private volatile long accumulatedElapsedMillis = 0L;
    private volatile int currentApm = 0;
    private volatile boolean closed = false;

    /**
     * @param windowService 前台窗口查询能力
     * @param tickCallback  每秒/状态变更时的快照发布回调
     */
    public ApmMonitorEngine(WindowService windowService, Consumer<ApmSnapshot> tickCallback) {
        this.windowService = Objects.requireNonNull(windowService, "windowService");
        this.tickCallback = Objects.requireNonNull(tickCallback, "tickCallback");
        scheduler.scheduleAtFixedRate(this::onTick, 1, 1, TimeUnit.SECONDS);
    }

    /**
     * 开关窗口过滤逻辑。
     */
    public void setFilterByWindow(boolean filterByWindow) {
        if (closed) {
            return;
        }
        this.filterByWindow = filterByWindow;
    }

    /**
     * 设置目标窗口标题关键字（会去掉前后空白）。
     */
    public void setTargetWindowTitle(String targetWindowTitle) {
        if (closed) {
            return;
        }
        this.targetWindowTitle = targetWindowTitle == null ? "" : targetWindowTitle.trim();
    }

    /**
     * 开始监控会话。
     * <p>
     * 会重置当前秒事件计数，但不清空历史点（历史清理由 reset 控制）。
     */
    public void start() {
        if (closed) {
            return;
        }
        if (monitoring.compareAndSet(false, true)) {
            synchronized (stateLock) {
                sessionStartedAtMillis = System.currentTimeMillis();
                currentApm = 0;
                eventsInSecond.set(0);
            }
            publishSnapshotSafely();
        }
    }

    /**
     * 暂停监控会话并冻结计时。
     */
    public void pause() {
        if (closed) {
            return;
        }
        if (monitoring.compareAndSet(true, false)) {
            synchronized (stateLock) {
                if (sessionStartedAtMillis > 0L) {
                    accumulatedElapsedMillis += Math.max(0L, System.currentTimeMillis() - sessionStartedAtMillis);
                    sessionStartedAtMillis = 0L;
                }
                currentApm = 0;
                eventsInSecond.set(0);
            }
        }
        publishSnapshotSafely();
    }

    /**
     * @return 是否处于监控中
     */
    public boolean isMonitoring() {
        return monitoring.get();
    }

    /**
     * 重置统计数据与会话计时。
     */
    public void reset() {
        if (closed) {
            return;
        }
        synchronized (stateLock) {
            eventsInSecond.set(0);
            currentApm = 0;
            accumulatedElapsedMillis = 0L;
            sessionStartedAtMillis = isMonitoring() ? System.currentTimeMillis() : 0L;
        }
        synchronized (pointLock) {
            points.clear();
        }
        publishSnapshotSafely();
    }

    /**
     * 主动读取当前前台窗口标题。
     */
    public String getCurrentForegroundWindow() {
        if (closed) {
            return "未知窗口";
        }
        String title = safeWindowTitle(windowService.getForegroundWindowTitle());
        lastActiveWindow = title;
        return title;
    }

    /**
     * 输入事件入口。
     * <p>
     * 仅在监控中计数，并按窗口过滤规则决定是否纳入统计。
     */
    public void onInputEvent() {
        if (closed || !monitoring.get()) {
            return;
        }
        String activeWindow = safeWindowTitle(windowService.getForegroundWindowTitle());
        lastActiveWindow = activeWindow;
        if (matchesWindow(activeWindow)) {
            eventsInSecond.incrementAndGet();
        }
    }

    private boolean matchesWindow(String activeWindow) {
        if (!filterByWindow) {
            return true;
        }
        if (targetWindowTitle.isBlank()) {
            // 开启“仅统计指定窗口”但未设置目标时，不应统计任何窗口。
            return false;
        }
        return activeWindow.toLowerCase(LOCALE).contains(targetWindowTitle.toLowerCase(LOCALE));
    }

    private void onTick() {
        if (closed || !monitoring.get()) {
            return;
        }
        // 即使无输入，也刷新前台窗口标题，避免 UI 显示陈旧。
        lastActiveWindow = safeWindowTitle(windowService.getForegroundWindowTitle());
        long events = eventsInSecond.getAndSet(0);
        currentApm = toSafeApm(events);
        long now = Instant.now().getEpochSecond();
        synchronized (pointLock) {
            points.add(new ApmPoint(now, currentApm));
            if (points.size() > MAX_POINTS) {
                points.remove(0);
            }
        }
        publishSnapshotSafely();
    }

    /**
     * 发布不可变快照。
     */
    private void publishSnapshot() {
        long nowEpochSecond = Instant.now().getEpochSecond();
        long elapsedSeconds = computeElapsedSeconds();
        List<ApmPoint> copy;
        synchronized (pointLock) {
            copy = new ArrayList<>(points);
        }
        tickCallback.accept(new ApmSnapshot(
                nowEpochSecond,
                currentApm,
                elapsedSeconds,
                lastActiveWindow,
                targetWindowTitle,
                filterByWindow,
                monitoring.get(),
                copy
        ));
    }

    /**
     * 安全发布快照，避免单次 UI/回调异常杀死调度线程。
     */
    private void publishSnapshotSafely() {
        try {
            publishSnapshot();
        } catch (RuntimeException ignored) {
            // Keep scheduler alive even if the callback fails once.
        }
    }

    /**
     * 计算“累计监控时长（秒）”。
     */
    private long computeElapsedSeconds() {
        synchronized (stateLock) {
            long elapsedMillis = accumulatedElapsedMillis;
            if (monitoring.get() && sessionStartedAtMillis > 0L) {
                elapsedMillis += Math.max(0L, System.currentTimeMillis() - sessionStartedAtMillis);
            }
            return elapsedMillis / 1000;
        }
    }

    /**
     * 将“每秒事件数”转换为 APM（每分钟事件数），并防溢出。
     */
    private int toSafeApm(long eventsInOneSecond) {
        long candidate = eventsInOneSecond * 60L;
        return candidate > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) candidate;
    }

    /**
     * 统一窗口名兜底，避免 null/blank 进入上层显示。
     */
    private String safeWindowTitle(String title) {
        if (title == null || title.isBlank()) {
            return "未知窗口";
        }
        return title;
    }

    /**
     * 关闭引擎并停止后台调度。
     */
    @Override
    public void close() {
        closed = true;
        monitoring.set(false);
        eventsInSecond.set(0);
        scheduler.shutdownNow();
    }

    private static class TickThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "apm-monitor-tick");
            // 设为守护线程，避免阻塞 JVM 退出。
            thread.setDaemon(true);
            return thread;
        }
    }
}
