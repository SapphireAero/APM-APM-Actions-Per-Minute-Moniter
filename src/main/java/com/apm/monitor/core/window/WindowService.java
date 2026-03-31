package com.apm.monitor.core.window;

/**
 * 前台窗口查询接口。
 */
public interface WindowService {
    /**
     * @return 当前前台窗口标题，未知或不可用时由实现返回兜底文案
     */
    String getForegroundWindowTitle();
}
