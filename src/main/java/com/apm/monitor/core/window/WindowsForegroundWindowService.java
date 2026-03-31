package com.apm.monitor.core.window;

import com.sun.jna.Platform;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;

/**
 * Windows 平台前台窗口实现。
 * <p>
 * 通过 JNA 调用 User32 API 读取当前前台窗口标题。
 */
public class WindowsForegroundWindowService implements WindowService {
    private static final int TITLE_BUFFER_SIZE = 1024;

    /**
     * 获取当前前台窗口标题。
     *
     * @return 窗口标题；若非 Windows 或查询失败，返回兜底文案
     */
    @Override
    public String getForegroundWindowTitle() {
        if (!Platform.isWindows()) {
            return "非Windows系统";
        }
        WinDef.HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        if (hwnd == null) {
            return "未知窗口";
        }
        char[] buffer = new char[TITLE_BUFFER_SIZE];
        int len = User32.INSTANCE.GetWindowText(hwnd, buffer, TITLE_BUFFER_SIZE);
        if (len <= 0) {
            return "未知窗口";
        }
        return new String(buffer, 0, len).trim();
    }
}
