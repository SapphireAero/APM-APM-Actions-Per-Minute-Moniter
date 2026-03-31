package com.apm.monitor.ui;

import com.apm.monitor.core.api.ApmBackend;
import com.apm.monitor.core.stats.ApmStatsCalculator;
import com.apm.monitor.model.ApmPoint;
import com.apm.monitor.model.ApmSnapshot;
import com.apm.monitor.model.ChartStep;
import com.apm.monitor.model.StatsRange;
import com.github.kwhat.jnativehook.NativeHookException;
import javafx.animation.PauseTransition;
import javafx.scene.control.Button;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 仪表盘控制器。
 * <p>
 * 负责：
 * 1) UI 事件绑定；
 * 2) 后端调用协调；
 * 3) 快照到 UI 的状态映射。
 */
public class DashboardController implements AutoCloseable {
    private static final Color STATUS_RUNNING = Color.web("#0f7c4f");
    private static final Color STATUS_PAUSED = Color.web("#996300");
    private static final Color STATUS_ERROR = Color.web("#9d1d1d");
    private static final Color STATUS_DEFAULT = Color.web("#2d5a8a");
    private static final Color STATUS_WARNING = Color.web("#a56200");
    private static final Locale LOCALE = Locale.ROOT;
    private static final String TEXT_MONITOR_START = "开始监控";
    private static final String TEXT_MONITOR_PAUSE = "暂停监控";
    private static final String TEXT_MONITOR_RUNNING_STATUS = "监听中";
    private static final String TEXT_MONITOR_PAUSED_STATUS = "已暂停";
    private static final String TEXT_MONITOR_FAILED_STATUS = "监听启动失败";
    private static final String TEXT_MONITOR_NOT_STARTED_STATUS = "未启动";
    private static final String TEXT_ALL_WINDOWS = "监听窗口：全部窗口";
    private static final String TEXT_WINDOW_NOT_SET = "监听窗口：未设置目标窗口";
    private static final String TEXT_WINDOW_PREFIX = "监听窗口：";
    private static final String TEXT_CAPTURE_BUTTON_DEFAULT = "捕获当前窗口";
    private static final String TEXT_CAPTURE_BUTTON_PREPARING = "请切到目标窗口...";
    private static final double CAPTURE_DELAY_SECONDS = 2.5;

    /**
     * 只负责展示，不做业务处理。
     */
    private final DashboardView dashboardView;
    /**
     * 后端门面接口：控制器只依赖接口。
     */
    private ApmBackend backendService;
    /**
     * 最近一次快照，用于范围切换/步长切换时重算展示。
     */
    private ApmSnapshot lastSnapshot;
    /**
     * 防止重复初始化导致重复绑定监听器。
     */
    private boolean initialized;
    /**
     * 延时捕获任务，避免按钮点击瞬间总是捕获到本应用窗口。
     */
    private PauseTransition pendingCapture;

    public DashboardController(DashboardView dashboardView) {
        this.dashboardView = Objects.requireNonNull(dashboardView, "dashboardView");
    }

    /**
     * @return 绑定的视图对象
     */
    public DashboardView view() {
        return dashboardView;
    }

    /**
     * 绑定后端门面。仅允许绑定一次。
     */
    public void bindBackend(ApmBackend backendService) {
        if (this.backendService != null) {
            throw new IllegalStateException("Backend service is already bound.");
        }
        this.backendService = Objects.requireNonNull(backendService, "backendService");
    }

    /**
     * 初始化控制器（幂等）。
     */
    public void initialize() {
        if (initialized) {
            return;
        }
        backend();
        bindUiEvents();
        startNativeHook();
        backend().setTargetWindowTitle(dashboardView.targetWindowField().getText());
        backend().setFilterByWindow(false);
        initialized = true;
    }

    /**
     * 接收引擎快照并刷新界面。
     */
    public void onSnapshot(ApmSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        this.lastSnapshot = snapshot;
        refreshFromSnapshot();
    }

    /**
     * 关闭控制器及其关联后端资源。
     */
    @Override
    public void close() {
        if (pendingCapture != null) {
            pendingCapture.stop();
            pendingCapture = null;
        }
        if (backendService != null) {
            backendService.close();
        }
    }

    /**
     * 绑定 UI 组件事件。
     */
    private void bindUiEvents() {
        dashboardView.settingsButton().setOnAction(e ->
                dashboardView.showInfo("指定窗口说明", "窗口标题关键字用于过滤统计，支持模糊匹配。\n点击“捕获当前窗口”后，请立即切换到目标窗口。"));

        dashboardView.captureWindowButton().setOnAction(e -> scheduleWindowCapture());

        dashboardView.resetButton().setOnAction(e -> backend().resetMonitoring());
        dashboardView.monitorButton().setOnAction(e -> toggleMonitoring());

        dashboardView.targetWindowField().textProperty().addListener((obs, oldValue, newValue) -> {
            backend().setTargetWindowTitle(newValue);
            updateBottomWindowLabel(dashboardView.filterByWindowCheck().isSelected(), newValue);
        });
        dashboardView.filterByWindowCheck().selectedProperty().addListener((obs, oldValue, newValue) -> {
            backend().setFilterByWindow(newValue);
            updateBottomWindowLabel(newValue, dashboardView.targetWindowField().getText());
        });
        dashboardView.chartStepBox().valueProperty().addListener((obs, oldValue, newValue) -> refreshChart());
        dashboardView.statsRangeBox().valueProperty().addListener((obs, oldValue, newValue) -> refreshFromSnapshot());
    }

    /**
     * 延时捕获当前前台窗口，给用户留出切换目标窗口的时间。
     */
    private void scheduleWindowCapture() {
        if (pendingCapture != null) {
            pendingCapture.stop();
        }

        Button captureButton = dashboardView.captureWindowButton();
        captureButton.setDisable(true);
        captureButton.setText(TEXT_CAPTURE_BUTTON_PREPARING);

        pendingCapture = new PauseTransition(Duration.seconds(CAPTURE_DELAY_SECONDS));
        pendingCapture.setOnFinished(event -> {
            String currentWindow = backend().captureCurrentForegroundWindow();
            dashboardView.targetWindowField().setText(currentWindow);

            // 捕获成功后自动开启“仅统计指定窗口”，减少手动操作成本。
            if (!dashboardView.filterByWindowCheck().isSelected()) {
                dashboardView.filterByWindowCheck().setSelected(true);
            }

            captureButton.setDisable(false);
            captureButton.setText(TEXT_CAPTURE_BUTTON_DEFAULT);
            pendingCapture = null;
        });
        pendingCapture.playFromStart();
    }

    /**
     * 切换监控状态（开始/暂停）。
     */
    private void toggleMonitoring() {
        if (backend().isMonitoring()) {
            backend().pauseMonitoring();
            dashboardView.monitorButton().setText(TEXT_MONITOR_START);
            dashboardView.setMonitorStatus(TEXT_MONITOR_PAUSED_STATUS, STATUS_PAUSED);
        } else {
            backend().startMonitoring();
            dashboardView.monitorButton().setText(TEXT_MONITOR_PAUSE);
            dashboardView.setMonitorStatus(TEXT_MONITOR_RUNNING_STATUS, STATUS_RUNNING);
        }
    }

    /**
     * 基于最近快照刷新所有核心展示区域。
     */
    private void refreshFromSnapshot() {
        if (lastSnapshot == null) {
            return;
        }
        StatsRange range = selectedStatsRange();
        List<ApmPoint> rangedPoints = ApmStatsCalculator.filterByRange(
                lastSnapshot.points(),
                range.seconds(),
                lastSnapshot.snapshotEpochSecond()
        );
        dashboardView.setRealtimeApm(lastSnapshot.currentApm());
        dashboardView.setAverageApm(ApmStatsCalculator.average(rangedPoints));
        dashboardView.setPeakApm(ApmStatsCalculator.peak(rangedPoints));
        dashboardView.setActiveWindow("当前窗口：" + lastSnapshot.activeWindowTitle());
        dashboardView.setTimerText("统计计时：" + formatSeconds(lastSnapshot.elapsedSeconds()));
        updateGameStatus(lastSnapshot);
        updateBottomWindowLabel(lastSnapshot.filterByWindow(), lastSnapshot.targetWindowTitle());

        if (!backend().isHookReady()) {
            dashboardView.setMonitorStatus(TEXT_MONITOR_FAILED_STATUS, STATUS_ERROR);
        } else if (lastSnapshot.monitoring()) {
            dashboardView.setMonitorStatus(TEXT_MONITOR_RUNNING_STATUS, STATUS_RUNNING);
            dashboardView.monitorButton().setText(TEXT_MONITOR_PAUSE);
        } else {
            dashboardView.setMonitorStatus(TEXT_MONITOR_PAUSED_STATUS, STATUS_PAUSED);
            dashboardView.monitorButton().setText(TEXT_MONITOR_START);
        }
        refreshChart();
    }

    /**
     * 根据当前范围与步长重绘趋势图。
     */
    private void refreshChart() {
        if (lastSnapshot == null) {
            return;
        }
        StatsRange range = selectedStatsRange();
        ChartStep step = selectedChartStep();
        List<ApmPoint> rangedPoints = ApmStatsCalculator.filterByRange(
                lastSnapshot.points(),
                range.seconds(),
                lastSnapshot.snapshotEpochSecond()
        );
        List<ApmPoint> linePoints = ApmStatsCalculator.aggregateByStep(rangedPoints, step.seconds());
        dashboardView.setChartPoints(linePoints);
    }

    /**
     * 读取当前选中的统计范围，若为空使用默认值。
     */
    private StatsRange selectedStatsRange() {
        StatsRange range = dashboardView.statsRangeBox().getValue();
        return range == null ? StatsRange.ALL : range;
    }

    /**
     * 读取当前选中的图表步长，若为空使用默认值。
     */
    private ChartStep selectedChartStep() {
        ChartStep step = dashboardView.chartStepBox().getValue();
        return step == null ? ChartStep.STEP_5S : step;
    }

    /**
     * 刷新“游戏状态”展示。
     */
    private void updateGameStatus(ApmSnapshot snapshot) {
        if (!snapshot.filterByWindow()) {
            dashboardView.setGameStatus("全窗口统计", STATUS_DEFAULT);
            return;
        }
        if (snapshot.targetWindowTitle().isBlank()) {
            dashboardView.setGameStatus("未设置目标窗口", STATUS_WARNING);
            return;
        }
        boolean active = containsIgnoreCase(snapshot.activeWindowTitle(), snapshot.targetWindowTitle());
        if (active) {
            dashboardView.setGameStatus("目标窗口激活", STATUS_RUNNING);
        } else {
            dashboardView.setGameStatus("目标窗口未激活", STATUS_ERROR);
        }
    }

    /**
     * 刷新底部“监听窗口”提示文案。
     */
    private void updateBottomWindowLabel(boolean filterEnabled, String targetWindow) {
        if (!filterEnabled) {
            dashboardView.setListeningWindow(TEXT_ALL_WINDOWS);
            return;
        }
        if (targetWindow == null || targetWindow.isBlank()) {
            dashboardView.setListeningWindow(TEXT_WINDOW_NOT_SET);
            return;
        }
        dashboardView.setListeningWindow(TEXT_WINDOW_PREFIX + targetWindow);
    }

    /**
     * 忽略大小写包含匹配。
     */
    private boolean containsIgnoreCase(String value, String token) {
        if (value == null || token == null) {
            return false;
        }
        return value.toLowerCase(LOCALE).contains(token.toLowerCase(LOCALE));
    }

    /**
     * 格式化秒数为 HH:mm:ss。
     */
    private String formatSeconds(long seconds) {
        long hour = seconds / 3600;
        long minute = (seconds % 3600) / 60;
        long second = seconds % 60;
        return String.format("%02d:%02d:%02d", hour, minute, second);
    }

    /**
     * 启动全局钩子并更新相关 UI 状态。
     */
    private void startNativeHook() {
        try {
            backend().startHook();
            dashboardView.setMonitorStatus(TEXT_MONITOR_NOT_STARTED_STATUS, STATUS_DEFAULT);
        } catch (NativeHookException | RuntimeException exception) {
            dashboardView.showInfo("全局监听启动失败", "无法注册键鼠钩子，APM将无法统计。\n" + exception.getMessage());
            dashboardView.monitorButton().setDisable(true);
            dashboardView.setMonitorStatus(TEXT_MONITOR_FAILED_STATUS, STATUS_ERROR);
        }
    }

    /**
     * 获取已绑定后端，不存在时抛出明确异常。
     */
    private ApmBackend backend() {
        if (backendService == null) {
            throw new IllegalStateException("Backend service is not bound.");
        }
        return backendService;
    }
}
