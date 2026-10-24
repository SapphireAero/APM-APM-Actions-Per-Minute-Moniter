package com.apm.monitor.ui;

import com.apm.monitor.core.api.ApmBackend;
import com.apm.monitor.core.engine.ApmMonitorEngine;
import com.apm.monitor.model.ApmPoint;
import com.apm.monitor.model.ApmSnapshot;
import com.apm.monitor.model.StatsRange;
import javafx.application.Platform;
import javafx.scene.chart.LineChart;
import javafx.scene.control.Label;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class DashboardControllerTest {
    @BeforeAll
    static void initializeJavaFxWithoutShowingAWindow() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        Platform.startup(ready::countDown);
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }

    @AfterAll
    static void stopJavaFx() {
        Platform.exit();
    }

    @Test
    void pausedFilterRemainsCorrectAfterChangingStatisticsRange() throws Exception {
        onFxThread(() -> {
            DashboardView view = new DashboardView();
            try (DashboardController controller = new DashboardController(view)) {
                controller.bindBackend(new TestBackend(controller::onSnapshot));
                controller.initialize();
                view.targetWindowField().setText("Game");
                view.filterByWindowCheck().setSelected(true);
                controllerBackend(controller).captureCurrentForegroundWindow();
                assertEquals("监听窗口：Game", label(view, "listeningWindowValue"));
                assertEquals("目标窗口激活", label(view, "gameStatusValue"));
                view.statsRangeBox().setValue(StatsRange.LAST_1_MIN);
                assertEquals("监听窗口：Game", label(view, "listeningWindowValue"));
                assertEquals("目标窗口激活", label(view, "gameStatusValue"));
                view.monitorButton().fire();
                assertEquals("监听中（Raw Input）", label(view, "monitorStatusValue"));
            }
        });
    }

    @Test
    void allRangeUsesFullSessionMetricsWhileRecentRangeUsesRetainedPoints() throws Exception {
        onFxThread(() -> {
            DashboardView view = new DashboardView();
            try (DashboardController controller = new DashboardController(view)) {
                controller.bindBackend(new TestBackend(controller::onSnapshot));
                controller.initialize();
                controller.onSnapshot(new ApmSnapshot(1000, 0, 1000, "Game", "", false, false,
                        List.of(new ApmPoint(999, 120), new ApmPoint(1000, 0)), 240, 600, true));
                assertEquals("240", label(view, "averageValue"));
                assertEquals("600", label(view, "peakValue"));
                LineChart<?, ?> chart = (LineChart<?, ?>) field(DashboardView.class, "chart").get(view);
                assertTrue(chart.getTitle().contains("仅显示最近记录"));
                view.statsRangeBox().setValue(StatsRange.LAST_1_MIN);
                assertEquals("60", label(view, "averageValue"));
                assertEquals("120", label(view, "peakValue"));
                assertEquals("APM 波动趋势", chart.getTitle());
                view.statsRangeBox().setValue(StatsRange.ALL);
                assertEquals("240", label(view, "averageValue"));
                assertEquals("600", label(view, "peakValue"));
            }
        });
    }

    private static void onFxThread(FxAction action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> {
            action.run();
            return null;
        });
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static String label(DashboardView view, String name) throws Exception {
        return ((Label) field(DashboardView.class, name).get(view)).getText();
    }

    private static ApmBackend controllerBackend(DashboardController controller) throws Exception {
        return (ApmBackend) field(DashboardController.class, "backendService").get(controller);
    }

    @FunctionalInterface
    private interface FxAction {
        void run() throws Exception;
    }

    private static final class TestBackend implements ApmBackend {
        private final ApmMonitorEngine engine;

        TestBackend(Consumer<ApmSnapshot> snapshots) throws Exception {
            engine = new ApmMonitorEngine(() -> "Game", snapshots);
            ((ScheduledExecutorService) field(ApmMonitorEngine.class, "scheduler").get(engine)).shutdownNow();
        }

        public void startHook() { }
        public boolean isHookReady() { return true; }
        public String inputSourceName() { return "Raw Input"; }
        public void setTargetWindowTitle(String title) { engine.setTargetWindowTitle(title); }
        public void setFilterByWindow(boolean enabled) { engine.setFilterByWindow(enabled); }
        public void startMonitoring() { engine.start(); }
        public void pauseMonitoring() { engine.pause(); }
        public boolean isMonitoring() { return engine.isMonitoring(); }
        public void resetMonitoring() { engine.reset(); }
        public String captureCurrentForegroundWindow() { return engine.getCurrentForegroundWindow(); }
        public void close() { engine.close(); }
    }
}
