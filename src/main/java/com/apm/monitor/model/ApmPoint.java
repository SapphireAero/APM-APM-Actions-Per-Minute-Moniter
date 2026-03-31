package com.apm.monitor.model;

/**
 * APM 时间点数据。
 *
 * @param epochSecond 秒级时间戳
 * @param apm         对应时刻的 APM 值
 */
public record ApmPoint(long epochSecond, int apm) {
}
