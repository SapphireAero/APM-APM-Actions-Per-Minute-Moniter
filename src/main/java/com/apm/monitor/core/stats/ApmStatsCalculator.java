package com.apm.monitor.core.stats;

import com.apm.monitor.model.ApmPoint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 统计计算工具类（纯函数，无状态）。
 */
public final class ApmStatsCalculator {
    private ApmStatsCalculator() {
    }

    /**
     * 按当前时间过滤统计范围。
     *
     * @param points  原始点序列
     * @param seconds 时间窗口秒数，null 表示全量
     * @return 过滤后的点序列
     */
    public static List<ApmPoint> filterByRange(List<ApmPoint> points, Integer seconds) {
        return filterByRange(points, seconds, Instant.now().getEpochSecond());
    }

    /**
     * 按指定参考时间过滤统计范围。
     *
     * @param points               原始点序列
     * @param seconds              时间窗口秒数，null 表示全量
     * @param referenceEpochSecond 参考时间戳（秒）
     * @return 过滤后的点序列
     */
    public static List<ApmPoint> filterByRange(List<ApmPoint> points, Integer seconds, long referenceEpochSecond) {
        if (points == null || points.isEmpty()) {
            return Collections.emptyList();
        }
        if (seconds == null) {
            // 返回副本，避免调用方修改原集合。
            return new ArrayList<>(points);
        }
        long cutoff = referenceEpochSecond - seconds;
        List<ApmPoint> result = new ArrayList<>();
        for (ApmPoint point : points) {
            if (point.epochSecond() >= cutoff) {
                result.add(point);
            }
        }
        return result;
    }

    /**
     * 计算平均 APM（四舍五入）。
     */
    public static int average(List<ApmPoint> points) {
        if (points == null || points.isEmpty()) {
            return 0;
        }
        long sum = 0L;
        for (ApmPoint point : points) {
            sum += point.apm();
        }
        return (int) Math.round((double) sum / points.size());
    }

    /**
     * 计算峰值 APM。
     */
    public static int peak(List<ApmPoint> points) {
        if (points == null || points.isEmpty()) {
            return 0;
        }
        int max = 0;
        for (ApmPoint point : points) {
            if (point.apm() > max) {
                max = point.apm();
            }
        }
        return max;
    }

    /**
     * 按步长聚合（桶平均值）。
     *
     * @param points      原始点序列（按时间顺序）
     * @param stepSeconds 聚合步长（秒）
     * @return 聚合后的点序列
     */
    public static List<ApmPoint> aggregateByStep(List<ApmPoint> points, int stepSeconds) {
        if (points == null || points.isEmpty()) {
            return Collections.emptyList();
        }
        if (stepSeconds <= 1) {
            // 步长<=1 视为不聚合，返回副本避免外部修改。
            return new ArrayList<>(points);
        }
        List<ApmPoint> result = new ArrayList<>();
        long bucketStart = -1;
        long sum = 0L;
        int count = 0;
        for (ApmPoint point : points) {
            long pointBucket = (point.epochSecond() / stepSeconds) * stepSeconds;
            if (bucketStart == -1) {
                bucketStart = pointBucket;
            }
            if (pointBucket != bucketStart) {
                result.add(new ApmPoint(bucketStart, (int) Math.round((double) sum / count)));
                bucketStart = pointBucket;
                sum = 0L;
                count = 0;
            }
            sum += point.apm();
            count++;
        }
        if (count > 0) {
            result.add(new ApmPoint(bucketStart, (int) Math.round((double) sum / count)));
        }
        return result;
    }
}
