package com.apm.monitor.ui;

import com.apm.monitor.model.ApmPoint;
import com.apm.monitor.model.ChartStep;
import com.apm.monitor.model.StatsRange;
import com.apm.monitor.ui.style.DashboardStyleTokens;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;

/**
 * 仪表盘视图层（纯视图，不包含业务逻辑）。
 * <p>
 * 控制器通过公开的 getter/更新方法与该类交互。
 */
public class DashboardView {
    private static final String AUTHOR_TEXT = "作者：SapphireAero";
    private final BorderPane root = new BorderPane();
    private final Label realtimeValue = new Label("0");
    private final Label averageValue = new Label("0");
    private final Label peakValue = new Label("0");
    private final Label gameStatusValue = new Label("全窗口统计");
    private final Label monitorStatusValue = new Label("未启动");
    private final Label listeningWindowValue = new Label("监听窗口：全部窗口");
    private final Label timerValue = new Label("统计计时：00:00:00");
    private final Label activeWindowValue = new Label("当前窗口：未知窗口");

    private final TextField targetWindowField = new TextField();
    private final CheckBox filterByWindowCheck = new CheckBox("仅统计指定窗口");
    private final ComboBox<ChartStep> chartStepBox = new ComboBox<>();
    private final ComboBox<StatsRange> statsRangeBox = new ComboBox<>();

    private final Button monitorButton = new Button("开始监控");
    private final Button resetButton = new Button("重置统计");
    private final Button settingsButton = new Button("?");
    private final Button captureWindowButton = new Button("捕获当前窗口");
    private static final int HELP_BUTTON_WIDTH = 18;
    private static final int HELP_BUTTON_HEIGHT = 20;

    private final NumberAxis chartXAxis = new NumberAxis();
    private final NumberAxis chartYAxis = new NumberAxis();
    private final XYChart.Series<Number, Number> apmSeries = new XYChart.Series<>();
    private final LineChart<Number, Number> chart = new LineChart<>(chartXAxis, chartYAxis);

    /**
     * 构建完整视图树。
     */
    public DashboardView() {
        build();
    }

    public BorderPane root() {
        return root;
    }

    public Button monitorButton() {
        return monitorButton;
    }

    public Button resetButton() {
        return resetButton;
    }

    public Button settingsButton() {
        return settingsButton;
    }

    public Button captureWindowButton() {
        return captureWindowButton;
    }

    public TextField targetWindowField() {
        return targetWindowField;
    }

    public CheckBox filterByWindowCheck() {
        return filterByWindowCheck;
    }

    public ComboBox<ChartStep> chartStepBox() {
        return chartStepBox;
    }

    public ComboBox<StatsRange> statsRangeBox() {
        return statsRangeBox;
    }

    public void setRealtimeApm(int value) {
        realtimeValue.setText(String.valueOf(value));
    }

    public void setAverageApm(int value) {
        averageValue.setText(String.valueOf(value));
    }

    public void setPeakApm(int value) {
        peakValue.setText(String.valueOf(value));
    }

    public void setGameStatus(String text, Color color) {
        gameStatusValue.setText(text);
        gameStatusValue.setTextFill(color);
    }

    public void setMonitorStatus(String text, Color color) {
        monitorStatusValue.setText(text);
        monitorStatusValue.setTextFill(color);
    }

    public void setListeningWindow(String text) {
        listeningWindowValue.setText(text);
    }

    public void setTimerText(String text) {
        timerValue.setText(text);
    }

    public void setActiveWindow(String text) {
        activeWindowValue.setText(text);
    }

    /**
     * 将统计点映射到折线图序列。
     * X 轴使用“相对首点秒偏移”，避免直接展示 epoch 值。
     */
    public void setChartPoints(List<ApmPoint> points) {
        apmSeries.getData().clear();
        if (points == null || points.isEmpty()) {
            return;
        }
        long baseEpochSecond = points.get(0).epochSecond();
        for (ApmPoint point : points) {
            apmSeries.getData().add(new XYChart.Data<>(point.epochSecond() - baseEpochSecond, point.apm()));
        }
    }

    public void showInfo(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.show();
    }

    /**
     * 创建整体布局骨架。
     */
    private void build() {
        root.setStyle(DashboardStyleTokens.ROOT_BACKGROUND);

        VBox container = new VBox(10);
        container.setPadding(new Insets(12));
        container.getChildren().addAll(
                createTopControlArea(),
                createMetricArea(),
                createStatusInfoArea(),
                createChartArea(),
                createBottomStatusBar()
        );
        root.setCenter(container);
    }

    /**
     * 顶部：标题 + 操作按钮 + 过滤控件。
     */
    private VBox createTopControlArea() {
        Label title = new Label("APM 实时监控面板");
        title.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 22));
        title.setTextFill(DashboardStyleTokens.TITLE_COLOR);

        HBox topHeader = new HBox(10, title, new HBox());
        HBox.setHgrow(topHeader.getChildren().get(1), Priority.ALWAYS);
        HBox buttonArea = new HBox(8, monitorButton, resetButton);
        buttonArea.setAlignment(Pos.CENTER_RIGHT);
        topHeader.getChildren().add(buttonArea);
        topHeader.setAlignment(Pos.CENTER_LEFT);

        targetWindowField.setPromptText("输入窗口标题关键字");
        chartStepBox.setItems(FXCollections.observableArrayList(ChartStep.values()));
        chartStepBox.setValue(ChartStep.STEP_5S);
        statsRangeBox.setItems(FXCollections.observableArrayList(StatsRange.values()));
        statsRangeBox.setValue(StatsRange.ALL);

        HBox filterRow = new HBox(8,
                filterByWindowCheck,
                targetWindowField,
                captureWindowButton,
                settingsButton,
                new Label("曲线步长"),
                chartStepBox,
                new Label("统计范围"),
                statsRangeBox
        );
        settingsButton.setMinSize(HELP_BUTTON_WIDTH, HELP_BUTTON_HEIGHT);
        settingsButton.setPrefSize(HELP_BUTTON_WIDTH, HELP_BUTTON_HEIGHT);
        settingsButton.setMaxSize(HELP_BUTTON_WIDTH, HELP_BUTTON_HEIGHT);
        settingsButton.setStyle("-fx-background-radius: 12; -fx-padding: 0; -fx-font-weight: bold;");
        HBox.setHgrow(targetWindowField, Priority.ALWAYS);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        VBox topArea = new VBox(8, topHeader, filterRow);
        topArea.setPadding(new Insets(10));
        topArea.setStyle(DashboardStyleTokens.PANEL_CARD);
        return topArea;
    }

    /**
     * 核心数据卡片：实时/平均/峰值。
     */
    private HBox createMetricArea() {
        HBox metrics = new HBox(10,
                createMetricCard("实时 APM", realtimeValue),
                createMetricCard("平均 APM", averageValue),
                createMetricCard("峰值 APM", peakValue)
        );
        HBox.setHgrow(metrics.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(metrics.getChildren().get(1), Priority.ALWAYS);
        HBox.setHgrow(metrics.getChildren().get(2), Priority.ALWAYS);
        return metrics;
    }

    /**
     * 通用指标卡片。
     */
    private VBox createMetricCard(String title, Label valueLabel) {
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.SEMI_BOLD, 15));
        titleLabel.setTextFill(DashboardStyleTokens.METRIC_TITLE_COLOR);

        valueLabel.setFont(Font.font("Consolas", FontWeight.BOLD, 34));
        valueLabel.setTextFill(DashboardStyleTokens.METRIC_VALUE_COLOR);

        VBox card = new VBox(8, titleLabel, valueLabel);
        card.setPadding(new Insets(14));
        card.setMinHeight(120);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle(DashboardStyleTokens.METRIC_CARD);
        return card;
    }

    /**
     * 状态区：游戏状态 + 监控运行状态。
     */
    private HBox createStatusInfoArea() {
        VBox left = createStatusBlock("游戏状态", gameStatusValue);
        VBox right = createStatusBlock("监控运行状态", monitorStatusValue);
        HBox row = new HBox(10, left, right);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        row.setPrefHeight(52);
        return row;
    }

    /**
     * 通用状态块。
     */
    private VBox createStatusBlock(String title, Label value) {
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.SEMI_BOLD, 13));
        value.setFont(Font.font("Microsoft YaHei", FontWeight.NORMAL, 13));

        VBox block = new VBox(4, titleLabel, value);
        block.setPadding(new Insets(8, 12, 8, 12));
        block.setStyle(DashboardStyleTokens.STATUS_CARD);
        return block;
    }

    /**
     * 趋势图区域。
     */
    private VBox createChartArea() {
        chartXAxis.setLabel("时间(秒)");
        chartYAxis.setLabel("APM");
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setCreateSymbols(false);
        chart.setMinHeight(220);
        chart.setTitle("APM 波动趋势");
        chart.getData().add(apmSeries);

        Label authorLabel = new Label(AUTHOR_TEXT);
        authorLabel.setFont(Font.font("Microsoft YaHei", FontWeight.NORMAL, 11));
        authorLabel.setTextFill(Color.web("#5d6f84"));

        StackPane spacer = new StackPane();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox authorRow = new HBox(6, spacer, authorLabel);
        authorRow.setAlignment(Pos.CENTER_RIGHT);
        authorRow.setPadding(new Insets(4, 2, 0, 2));

        VBox box = new VBox(chart, authorRow);
        box.setPadding(new Insets(8));
        box.setStyle(DashboardStyleTokens.CHART_CARD);
        return box;
    }

    /**
     * 底部状态栏：当前窗口、监听窗口、计时。
     */
    private HBox createBottomStatusBar() {
        activeWindowValue.setFont(Font.font("Microsoft YaHei", 13));
        listeningWindowValue.setFont(Font.font("Microsoft YaHei", 13));
        timerValue.setFont(Font.font("Consolas", FontWeight.BOLD, 13));

        VBox left = new VBox(2, activeWindowValue, listeningWindowValue);
        StackPane spacer = new StackPane();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bottom = new HBox(10, left, spacer, timerValue);
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.setPadding(new Insets(8, 10, 8, 10));
        bottom.setStyle(DashboardStyleTokens.BOTTOM_BAR);
        activeWindowValue.setTextFill(DashboardStyleTokens.BOTTOM_ACTIVE_WINDOW_COLOR);
        listeningWindowValue.setTextFill(DashboardStyleTokens.BOTTOM_LISTENING_WINDOW_COLOR);
        timerValue.setTextFill(DashboardStyleTokens.BOTTOM_TIMER_COLOR);
        bottom.setPrefHeight(38);
        return bottom;
    }
}
