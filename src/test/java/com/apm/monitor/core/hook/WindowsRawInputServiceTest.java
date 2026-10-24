package com.apm.monitor.core.hook;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
class WindowsRawInputServiceTest {
    @Test
    void nativeReceiverRegistersAnInvisibleWindowAndCleansUpBeforeRestart() throws Exception {
        try (WindowsRawInputService receiver = new WindowsRawInputService(() -> { })) {
            receiver.start();
            assertTrue(receiver.isRunning());
            Field field = WindowsRawInputService.class.getDeclaredField("window");
            field.setAccessible(true);
            WinDef.HWND window = (WinDef.HWND) field.get(receiver);
            assertTrue(User32.INSTANCE.IsWindow(window));
            assertFalse(User32.INSTANCE.IsWindowVisible(window));
            receiver.close();
            assertFalse(receiver.isRunning());
            assertFalse(User32.INSTANCE.IsWindow(window));
            receiver.start();
            assertTrue(receiver.isRunning());
        }
    }

    @Test
    void windowsSelectsExactlyOneRawInputSourceAndCanCloseRepeatedly() throws Exception {
        try (NativeHookService service = new NativeHookService(() -> { })) {
            service.start();
            service.start();
            assertTrue(service.isReady());
            assertEquals("Raw Input", service.inputSourceName());
            assertFalse(GlobalScreen.isNativeHookRegistered());
            service.close();
            service.close();
            assertFalse(service.isReady());
        }
    }
}
