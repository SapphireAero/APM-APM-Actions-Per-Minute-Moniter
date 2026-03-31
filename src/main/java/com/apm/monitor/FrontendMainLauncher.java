package com.apm.monitor;

import javafx.application.Application;

/**
 * 前端主页面启动入口。
 * <p>
 * 该类是推荐的启动类，用于直接打开 APM 主监控界面。
 */
public final class FrontendMainLauncher {
    private FrontendMainLauncher() {
    }

    /**
     * 应用入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        Application.launch(ApmFxApplication.class, args);
    }
}
