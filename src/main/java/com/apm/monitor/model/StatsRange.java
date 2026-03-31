package com.apm.monitor.model;

/**
 * 统计时间范围枚举。
 */
public enum StatsRange {
    ALL("全程", null),
    LAST_1_MIN("最近1分钟", 60),
    LAST_5_MIN("最近5分钟", 300),
    LAST_15_MIN("最近15分钟", 900),
    LAST_30_MIN("最近30分钟", 1800);

    private final String label;
    private final Integer seconds;

    StatsRange(String label, Integer seconds) {
        this.label = label;
        this.seconds = seconds;
    }

    public Integer seconds() {
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
