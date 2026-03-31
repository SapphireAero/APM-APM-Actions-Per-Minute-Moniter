package com.apm.monitor.model;

/**
 * 图表聚合步长枚举。
 */
public enum ChartStep {
    STEP_5S("5s", 5),
    STEP_10S("10s", 10),
    STEP_30S("30s", 30);

    private final String label;
    private final int seconds;

    ChartStep(String label, int seconds) {
        this.label = label;
        this.seconds = seconds;
    }

    public int seconds() {
        return seconds;
    }

    /**
     * 下拉框展示文本。
     */
    @Override
    public String toString() {
        return label;
    }
}
