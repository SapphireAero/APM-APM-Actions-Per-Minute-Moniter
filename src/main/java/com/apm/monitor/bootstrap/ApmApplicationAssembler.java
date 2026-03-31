package com.apm.monitor.bootstrap;

import com.apm.monitor.core.api.ApmBackend;
import com.apm.monitor.core.service.ApmBackendService;
import com.apm.monitor.core.window.WindowsForegroundWindowService;
import com.apm.monitor.ui.DashboardController;
import com.apm.monitor.ui.DashboardView;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Objects;

/**
 * JavaFX 应用装配器。
 * <p>
 * 该类统一处理“对象组装 + 舞台装配 + 生命周期初始化”，
 * 让 {@code ApmFxApplication} 只保留最小化生命周期代码。
 */
public final class ApmApplicationAssembler {
    private static final int WINDOW_WIDTH = 1020;
    private static final int WINDOW_HEIGHT = 620;
    private static final String WINDOW_TITLE = "APM Monitor";

    /**
     * 组装并展示主界面。
     *
     * @param stage JavaFX 顶层舞台
     * @return 已初始化的控制器，用于在 stop 生命周期中关闭资源
     */
    public DashboardController assembleAndShow(Stage stage) {
        Objects.requireNonNull(stage, "stage");

        DashboardView dashboardView = new DashboardView();
        DashboardController controller = new DashboardController(dashboardView);
        ApmBackend backend = createBackend(controller);
        controller.bindBackend(backend);

        Scene scene = new Scene(controller.view().root(), WINDOW_WIDTH, WINDOW_HEIGHT);
        stage.setScene(scene);
        stage.setTitle(WINDOW_TITLE);
        stage.show();

        controller.initialize();
        return controller;
    }

    /**
     * 创建后端门面并将快照回调切换到 JavaFX UI 线程。
     */
    private ApmBackend createBackend(DashboardController controller) {
        return new ApmBackendService(
                new WindowsForegroundWindowService(),
                snapshot -> Platform.runLater(() -> controller.onSnapshot(snapshot))
        );
    }
}
