package com.apm.monitor.core.engine;

import com.apm.monitor.core.window.WindowService;
import com.apm.monitor.model.ApmPoint;
import com.apm.monitor.model.ApmSnapshot;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
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
    // 仅限制趋势图历史；全程统计由独立累计值保存。
    private static final int MAX_POINTS = 86_400;
    private static final Locale LOCALE = Locale.ROOT;

    private final WindowService windowService;
    private final Consumer<ApmSnapshot> tickCallback;
    /**
     * 单线程调度器，保证 tick 顺序执行，避免并发聚合冲突。
     */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new TickThreadFactory());
    private final int historyLimit;
    private final Deque<ApmPoint> points = new ArrayDeque<>();
    /**
     * 所有统计状态与快照发布共用此锁，避免重置与采样相互覆盖。
     * 窗口查询在锁外进行，返回后通过会话代号检查结果是否仍然有效。
     */
    private final Object stateLock = new Object();
    private long eventsInSecond;
    private boolean monitoring;
    private long sessionGeneration;
    private String lastActiveWindow = "未知窗口";
    private String targetWindowTitle = "";
    private boolean filterByWindow;
    private long sessionStartedAtMillis;
    private long accumulatedElapsedMillis;
    private int currentApm;
    private boolean closed;
    private long totalPointCount;
    private long totalApmSum;
    private int allTimePeakApm;

    /**
     * @param windowService 前台窗口查询能力
     * @param tickCallback  每秒/状态变更时的非阻塞回调；UI 消费方应自行切线程
     */
    public ApmMonitorEngine(WindowService windowService, Consumer<ApmSnapshot> tickCallback) {
        this(windowService, tickCallback, MAX_POINTS);
    }

    ApmMonitorEngine(WindowService windowService, Consumer<ApmSnapshot> tickCallback, int historyLimit) {
        this.windowService = Objects.requireNonNull(windowService, "windowService");
        this.tickCallback = Objects.requireNonNull(tickCallback, "tickCallback");
        if (historyLimit <= 0) {
            throw new IllegalArgumentException("historyLimit must be positive");
        }
        this.historyLimit = historyLimit;
        scheduler.scheduleAtFixedRate(this::onTick, 1, 1, TimeUnit.SECONDS);
    }

    /**
     * 开关窗口过滤逻辑。
     */
    public void setFilterByWindow(boolean filterByWindow) {
        synchronized (stateLock) {
            if (closed || this.filterByWindow == filterByWindow) {
                return;
            }
            this.filterByWindow = filterByWindow;
            publishSnapshotLocked();
        }
    }

    /**
     * 设置目标窗口标题关键字（会去掉前后空白）。
     */
    public void setTargetWindowTitle(String targetWindowTitle) {
        String normalized = targetWindowTitle == null ? "" : targetWindowTitle.trim();
        synchronized (stateLock) {
            if (closed || this.targetWindowTitle.equals(normalized)) {
                return;
            }
            this.targetWindowTitle = normalized;
            publishSnapshotLocked();
        }
    }

    /**
     * 开始监控会话。
     * <p>
     * 会重置当前秒事件计数，但不清空历史点（历史清理由 reset 控制）。
     */
    public void start() {
        synchronized (stateLock) {
            if (closed || monitoring) {
                return;
            }
            sessionGeneration++;
            monitoring = true;
            sessionStartedAtMillis = System.currentTimeMillis();
            currentApm = 0;
            eventsInSecond = 0;
            publishSnapshotLocked();
        }
    }

    /**
     * 暂停监控会话并冻结计时。
     */
    public void pause() {
        synchronized (stateLock) {
            if (closed) {
                return;
            }
            if (monitoring) {
                monitoring = false;
                sessionGeneration++;
                // 暂停前保存尚未到达采样时刻的输入，避免恢复时被清零。
                if (eventsInSecond > 0) {
                    appendPointLocked(Instant.now().getEpochSecond(), toSafeApm(eventsInSecond));
                }
                if (sessionStartedAtMillis > 0L) {
                    accumulatedElapsedMillis += Math.max(0L, System.currentTimeMillis() - sessionStartedAtMillis);
                    sessionStartedAtMillis = 0L;
                }
                currentApm = 0;
                eventsInSecond = 0;
            }
            publishSnapshotLocked();
        }
    }

    /**
     * @return 是否处于监控中
     */
    public boolean isMonitoring() {
        synchronized (stateLock) {
            return monitoring;
        }
    }

    /**
     * 重置统计数据与会话计时。
     */
    public void reset() {
        synchronized (stateLock) {
            if (closed) {
                return;
            }
            sessionGeneration++;
            eventsInSecond = 0;
            currentApm = 0;
            accumulatedElapsedMillis = 0L;
            sessionStartedAtMillis = monitoring ? System.currentTimeMillis() : 0L;
            points.clear();
            totalPointCount = 0;
            totalApmSum = 0;
            allTimePeakApm = 0;
            publishSnapshotLocked();
        }
    }

    /**
     * 主动读取当前前台窗口标题。
     */
    public String getCurrentForegroundWindow() {
        synchronized (stateLock) {
            if (closed) {
                return "未知窗口";
            }
        }
        String title = readForegroundWindowTitle();
        synchronized (stateLock) {
            if (closed) {
                return "未知窗口";
            }
            lastActiveWindow = title;
            publishSnapshotLocked();
            return title;
        }
    }

    /**
     * 输入事件入口。
     * <p>
     * 仅在监控中计数，并按窗口过滤规则决定是否纳入统计。
     */
    public void onInputEvent() {
        long generation;
        synchronized (stateLock) {
            if (closed || !monitoring) {
                return;
            }
            generation = sessionGeneration;
        }
        String activeWindow = readForegroundWindowTitle();
        synchronized (stateLock) {
            if (closed || !monitoring || generation != sessionGeneration) {
                return;
            }
            lastActiveWindow = activeWindow;
            if (matchesWindow(activeWindow)) {
                eventsInSecond++;
            }
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
        long generation;
        synchronized (stateLock) {
            if (closed || !monitoring) {
                return;
            }
            generation = sessionGeneration;
        }
        // 即使无输入，也刷新前台窗口标题，避免 UI 显示陈旧。
        String activeWindow = readForegroundWindowTitle();
        synchronized (stateLock) {
            if (closed || !monitoring || generation != sessionGeneration) {
                return;
            }
            lastActiveWindow = activeWindow;
            currentApm = toSafeApm(eventsInSecond);
            eventsInSecond = 0;
            appendPointLocked(Instant.now().getEpochSecond(), currentApm);
            publishSnapshotLocked();
        }
    }

    private void appendPointLocked(long epochSecond, int apm) {
        points.addLast(new ApmPoint(epochSecond, apm));
        totalPointCount++;
        totalApmSum += apm;
        allTimePeakApm = Math.max(allTimePeakApm, apm);
        if (points.size() > historyLimit) {
            points.removeFirst();
        }
    }

    /**
     * 在 stateLock 内按状态变更顺序发布，避免旧快照晚于 reset 快照到达 UI。
     * 回调只负责非阻塞地传递快照，单次回调异常不影响后续采样。
     */
    private void publishSnapshotLocked() {
        try {
            int average = totalPointCount == 0 ? 0 : (int) Math.round((double) totalApmSum / totalPointCount);
            tickCallback.accept(new ApmSnapshot(
                    Instant.now().getEpochSecond(),
                    currentApm,
                    computeElapsedSecondsLocked(),
                    lastActiveWindow,
                    targetWindowTitle,
                    filterByWindow,
                    monitoring,
                    new ArrayList<>(points),
                    average,
                    allTimePeakApm,
                    totalPointCount > points.size()
            ));
        } catch (RuntimeException ignored) {
            // Keep scheduler alive even if the callback fails once.
        }
    }

    /**
     * 计算“累计监控时长（秒）”。
     */
    private long computeElapsedSecondsLocked() {
        long elapsedMillis = accumulatedElapsedMillis;
        if (monitoring && sessionStartedAtMillis > 0L) {
            elapsedMillis += Math.max(0L, System.currentTimeMillis() - sessionStartedAtMillis);
        }
        return elapsedMillis / 1000;
    }

    /**
     * 将“每秒事件数”转换为 APM（每分钟事件数），并防溢出。
     */
    private int toSafeApm(long eventsInOneSecond) {
        return eventsInOneSecond > Integer.MAX_VALUE / 60L ? Integer.MAX_VALUE : (int) (eventsInOneSecond * 60L);
    }

    private String readForegroundWindowTitle() {
        try {
            return safeWindowTitle(windowService.getForegroundWindowTitle());
        } catch (RuntimeException ignored) {
            return "未知窗口";
        }
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
        synchronized (stateLock) {
            closed = true;
            monitoring = false;
            sessionGeneration++;
            eventsInSecond = 0;
        }
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
