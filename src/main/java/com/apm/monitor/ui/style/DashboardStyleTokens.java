package com.apm.monitor.ui.style;

import javafx.scene.paint.Color;

/**
 * 仪表盘样式令牌。
 * <p>
 * 将常用颜色/样式字符串集中，降低 UI 样式分散硬编码。
 */
public final class DashboardStyleTokens {
    public static final String ROOT_BACKGROUND = "-fx-background-color: linear-gradient(to bottom right, #f6fbff, #e8f3ff);";
    public static final String PANEL_CARD = "-fx-background-color: rgba(255,255,255,0.88); -fx-background-radius: 12;";
    public static final String METRIC_CARD = "-fx-background-color: rgba(255,255,255,0.93); -fx-background-radius: 10;";
    public static final String STATUS_CARD = "-fx-background-color: rgba(255,255,255,0.9); -fx-background-radius: 8;";
    public static final String CHART_CARD = "-fx-background-color: rgba(255,255,255,0.93); -fx-background-radius: 10;";
    public static final String BOTTOM_BAR = "-fx-background-color: rgba(24,37,58,0.9); -fx-background-radius: 8;";

    public static final Color TITLE_COLOR = Color.web("#0f2747");
    public static final Color METRIC_TITLE_COLOR = Color.web("#284767");
    public static final Color METRIC_VALUE_COLOR = Color.web("#1c8e61");
    public static final Color BOTTOM_ACTIVE_WINDOW_COLOR = Color.WHITE;
    public static final Color BOTTOM_LISTENING_WINDOW_COLOR = Color.web("#d3e5ff");
    public static final Color BOTTOM_TIMER_COLOR = Color.web("#9ae6b4");

    private DashboardStyleTokens() {
    }
}
