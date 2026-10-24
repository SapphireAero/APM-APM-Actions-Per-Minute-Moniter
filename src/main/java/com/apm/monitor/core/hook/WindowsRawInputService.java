package com.apm.monitor.core.hook;

import com.github.kwhat.jnativehook.NativeHookException;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Windows 后台 Raw Input 接收器。
 * 消息窗口永不显示、不获取焦点；只注册 INPUTSINK，不使用 NOLEGACY 或捕获鼠标标志。
 * 全程由同一线程创建/销毁 HWND、注册/注销设备及处理 WM_INPUT。
 */
public final class WindowsRawInputService implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(WindowsRawInputService.class.getName());
    private static final int WM_INPUT = 0xff;
    private static final int RID_INPUT = 0x10000003;
    private static final int RIDEV_INPUTSINK = 0x100;
    private static final int RIDEV_REMOVE = 1;
    private static final int HEADER_SIZE = 8 + 2 * Native.POINTER_SIZE;
    private final Runnable eventCallback;
    private final WinUser.WindowProc windowProc = this::windowProcedure;
    private final Memory packet = new Memory(256);
    private volatile WinDef.HWND window;
    private volatile boolean closing;
    private volatile boolean running;
    private Thread thread;

    public WindowsRawInputService(Runnable eventCallback) {
        this.eventCallback = Objects.requireNonNull(eventCallback, "eventCallback");
    }

    public synchronized void start() throws NativeHookException {
        if (running) {
            return;
        }
        if (thread != null && thread.isAlive()) {
            throw new NativeHookException("Raw Input 接收线程尚未结束");
        }
        closing = false;
        CountDownLatch ready = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        thread = new Thread(() -> runMessageLoop(ready, failure), "apm-raw-input");
        thread.setDaemon(true);
        thread.start();
        try {
            if (!ready.await(5, TimeUnit.SECONDS)) {
                throw new NativeHookException("Raw Input 注册超时");
            }
            if (failure.get() != null || !running) {
                throw new NativeHookException("Raw Input 注册失败", failure.get());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            close();
            throw new NativeHookException("Raw Input 注册被中断", exception);
        } catch (NativeHookException exception) {
            close();
            throw exception;
        }
    }

    public boolean isRunning() {
        return running;
    }

    private void runMessageLoop(CountDownLatch ready, AtomicReference<Throwable> failure) {
        String className = "APMMonitor.RawInput." + UUID.randomUUID();
        WinDef.HINSTANCE instance = null;
        boolean classRegistered = false;
        boolean devicesRegistered = false;
        try {
            instance = Kernel32.INSTANCE.GetModuleHandle(null);
            WinUser.WNDCLASSEX windowClass = new WinUser.WNDCLASSEX();
            windowClass.cbSize = windowClass.size();
            windowClass.hInstance = instance;
            windowClass.lpszClassName = className;
            windowClass.lpfnWndProc = windowProc;
            WinDef.ATOM atom = User32.INSTANCE.RegisterClassEx(windowClass);
            if (atom == null || atom.intValue() == 0) {
                throw win32Failure("RegisterClassEx");
            }
            classRegistered = true;
            // JNA 5.14 的 HWND_MESSAGE 使用 int 常量，在 x64 上被零扩展；这里保持指针有符号宽度。
            window = User32.INSTANCE.CreateWindowEx(0, className, "", 0, 0, 0, 0, 0,
                    new WinDef.HWND(Pointer.createConstant(-3L)), null, instance, null);
            if (window == null) {
                throw win32Failure("CreateWindowEx");
            }
            if (closing) {
                return;
            }
            registerDevices(window, RIDEV_INPUTSINK);
            devicesRegistered = true;
            running = !closing;
            ready.countDown();
            WinUser.MSG message = new WinUser.MSG();
            while (!closing) {
                int result = User32.INSTANCE.GetMessage(message, null, 0, 0);
                if (result == -1) {
                    throw win32Failure("GetMessage");
                }
                if (result == 0) {
                    break;
                }
                User32.INSTANCE.TranslateMessage(message);
                User32.INSTANCE.DispatchMessage(message);
            }
        } catch (Throwable exception) {
            failure.compareAndSet(null, exception);
            LOG.log(Level.WARNING, "Raw Input 接收器停止", exception);
        } finally {
            running = false;
            try {
                if (devicesRegistered) {
                    registerDevices(null, RIDEV_REMOVE);
                }
            } catch (RuntimeException exception) {
                LOG.log(Level.WARNING, "Raw Input 设备注销失败", exception);
            }
            WinDef.HWND oldWindow = window;
            window = null;
            if (oldWindow != null) {
                User32.INSTANCE.DestroyWindow(oldWindow);
            }
            if (classRegistered) {
                User32.INSTANCE.UnregisterClass(className, instance);
            }
            ready.countDown();
        }
    }

    private WinDef.LRESULT windowProcedure(WinDef.HWND hwnd, int message,
                                           WinDef.WPARAM wParam, WinDef.LPARAM lParam) {
        if (message == WM_INPUT && running && !closing) {
            try {
                IntByReference size = new IntByReference((int) packet.size());
                int bytes = RawUser32.INSTANCE.GetRawInputData(new Pointer(lParam.longValue()), RID_INPUT,
                        packet, size, HEADER_SIZE);
                if (bytes >= HEADER_SIZE && bytes <= packet.size()) {
                    int actions = RawInputDecoder.countActions(packet, bytes, HEADER_SIZE);
                    for (int i = 0; i < actions; i++) {
                        eventCallback.run();
                    }
                }
            } catch (RuntimeException exception) {
                LOG.log(Level.WARNING, "Raw Input 操作处理失败", exception);
            }
        } else if (message == WinUser.WM_CLOSE) {
            closing = true;
            User32.INSTANCE.PostQuitMessage(0);
            return new WinDef.LRESULT(0);
        }
        // 尤其是前台 RIM_INPUT，必须交回系统清理其数据包。
        return User32.INSTANCE.DefWindowProc(hwnd, message, wParam, lParam);
    }

    private static void registerDevices(WinDef.HWND target, int flags) {
        RawInputDevice[] devices = (RawInputDevice[]) new RawInputDevice().toArray(2);
        for (int i = 0; i < devices.length; i++) {
            devices[i].usUsagePage = 1;
            devices[i].usUsage = (short) (i == 0 ? 6 : 2);
            devices[i].dwFlags = flags;
            devices[i].hwndTarget = target;
            devices[i].write();
        }
        if (!RawUser32.INSTANCE.RegisterRawInputDevices(devices, devices.length, devices[0].size())) {
            throw win32Failure("RegisterRawInputDevices");
        }
    }

    private static IllegalStateException win32Failure(String operation) {
        return new IllegalStateException(operation + " failed, Win32 error=" + Native.getLastError());
    }

    @Override
    public synchronized void close() {
        closing = true;
        running = false;
        WinDef.HWND current = window;
        if (current != null) {
            User32.INSTANCE.PostMessage(current, WinUser.WM_CLOSE, new WinDef.WPARAM(0), new WinDef.LPARAM(0));
        }
        if (thread != null && thread != Thread.currentThread()) {
            try {
                thread.join(3000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public interface RawUser32 extends StdCallLibrary {
        RawUser32 INSTANCE = Native.load("user32", RawUser32.class, W32APIOptions.DEFAULT_OPTIONS);
        boolean RegisterRawInputDevices(RawInputDevice[] devices, int count, int structureSize);
        int GetRawInputData(Pointer handle, int command, Pointer data, IntByReference size, int headerSize);
    }

    @Structure.FieldOrder({"usUsagePage", "usUsage", "dwFlags", "hwndTarget"})
    public static class RawInputDevice extends Structure {
        public short usUsagePage;
        public short usUsage;
        public int dwFlags;
        public WinDef.HWND hwndTarget;
    }
}
