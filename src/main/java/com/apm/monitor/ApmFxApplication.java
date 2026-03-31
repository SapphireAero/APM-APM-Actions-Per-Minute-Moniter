package com.apm.monitor;

import com.apm.monitor.bootstrap.ApmApplicationAssembler;
import com.apm.monitor.ui.DashboardController;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * JavaFX 主应用类。
 * <p>
 * 仅负责生命周期回调：
 * - {@link #start(Stage)}：创建并显示 UI；
 * - {@link #stop()}：释放控制器及后端资源。
 */
public class ApmFxApplication extends Application {
    private final ApmApplicationAssembler assembler = new ApmApplicationAssembler();
    private DashboardController dashboardController;

    /**
     * JavaFX 启动回调。
     */
    @Override
    public void start(Stage stage) {
        dashboardController = assembler.assembleAndShow(stage);
    }

    /**
     * JavaFX 关闭回调。
     */
    @Override
    public void stop() {
        if (dashboardController != null) {
            dashboardController.close();
        }
    }
}
