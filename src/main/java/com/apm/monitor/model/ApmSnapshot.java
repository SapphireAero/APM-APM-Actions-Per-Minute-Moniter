package com.apm.monitor.model;

import java.util.List;
import java.util.Objects;

/**
 * 引擎快照。
 * <p>
 * 每次发布都代表某一时刻的完整可展示状态，便于 UI 进行“全量刷新”。
 * 该模型与 {@link ApmPoint}、{@link ChartStep}、{@link StatsRange}
 * 一起构成领域模型层的不可变数据对象。
 *
 * @param snapshotEpochSecond 快照时间戳（秒）
 * @param currentApm          当前秒折算的 APM
 * @param elapsedSeconds      累计监控时长（秒）
 * @param activeWindowTitle   当前前台窗口标题
 * @param targetWindowTitle   目标窗口关键字
 * @param filterByWindow      是否启用窗口过滤
 * @param monitoring          当前是否处于监控状态
 * @param points              历史点序列（不可变副本）
 */
public record ApmSnapshot(
        long snapshotEpochSecond,
        int currentApm,
        long elapsedSeconds,
        String activeWindowTitle,
        String targetWindowTitle,
        boolean filterByWindow,
        boolean monitoring,
        List<ApmPoint> points
) {
    /**
     * 规范化构造，确保快照对象对上层是安全且可预测的。
     */
    public ApmSnapshot {
        activeWindowTitle = Objects.requireNonNullElse(activeWindowTitle, "未知窗口");
        targetWindowTitle = Objects.requireNonNullElse(targetWindowTitle, "");
        points = points == null ? List.of() : List.copyOf(points);
    }
}
