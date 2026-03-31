package com.apm.monitor.core.api;

import com.github.kwhat.jnativehook.NativeHookException;

/**
 * APM 后端门面接口。
 * <p>
 * 控制层只依赖该接口，不直接依赖具体实现，便于后续替换实现或单元测试。
 * 核心后端采用分层结构：
 * API（门面接口）/ Service（组件编排）/ Engine（统计引擎）/
 * Stats（统计工具）/ Hook（全局输入监听）/ Window（前台窗口识别）。
 */
public interface ApmBackend extends AutoCloseable {

    /**
     * 启动全局输入钩子（键盘/鼠标）。
     *
     * @throws NativeHookException 当系统钩子注册失败时抛出
     */
    void startHook() throws NativeHookException;

    /**
     * @return 钩子是否可用
     */
    boolean isHookReady();

    /**
     * 设置窗口标题过滤关键字。
     *
     * @param title 目标窗口标题关键字
     */
    void setTargetWindowTitle(String title);

    /**
     * 设置是否只统计目标窗口。
     *
     * @param enabled true=仅统计目标窗口，false=统计所有窗口
     */
    void setFilterByWindow(boolean enabled);

    /**
     * 开始监控。
     */
    void startMonitoring();

    /**
     * 暂停监控。
     */
    void pauseMonitoring();

    /**
     * @return 当前是否处于监控状态
     */
    boolean isMonitoring();

    /**
     * 重置统计状态。
     */
    void resetMonitoring();

    /**
     * 捕获当前前台窗口标题。
     *
     * @return 当前窗口标题，未知时返回占位文案
     */
    String captureCurrentForegroundWindow();

    /**
     * 关闭后端资源（幂等）。
     */
    @Override
    void close();
}
