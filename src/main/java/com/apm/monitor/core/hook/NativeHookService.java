package com.apm.monitor.core.hook;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseInputListener;
import com.sun.jna.Platform;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Handler;
import java.util.logging.Logger;

/**
 * 全局输入钩子服务。
 * <p>
 * Windows 优先使用后台 Raw Input，注册失败才回退到 JNativeHook，避免两路重复计数。
 * 对外只暴露“启动/关闭 + 事件回调”两个概念。
 */
public class NativeHookService implements NativeKeyListener, NativeMouseInputListener, AutoCloseable {
    private final Runnable eventCallback;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private volatile WindowsRawInputService rawInput;
    private volatile boolean legacyRegistered;

    /**
     * @param eventCallback 任意键盘按下或鼠标按下时触发
     */
    public NativeHookService(Runnable eventCallback) {
        this.eventCallback = Objects.requireNonNull(eventCallback, "eventCallback");
        disableJNativeHookLogging();
    }

    /**
     * 启动全局钩子（幂等）。
     */
    public synchronized void start() throws NativeHookException {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        NativeHookException rawFailure = null;
        if (Platform.isWindows()) {
            WindowsRawInputService candidate = new WindowsRawInputService(eventCallback);
            try {
                candidate.start();
                rawInput = candidate;
                return;
            } catch (NativeHookException exception) {
                candidate.close();
                rawFailure = exception;
            }
        }
        try {
            GlobalScreen.registerNativeHook();
            legacyRegistered = true;
            GlobalScreen.addNativeKeyListener(this);
            GlobalScreen.addNativeMouseListener(this);
        } catch (NativeHookException exception) {
            close();
            if (rawFailure != null) {
                exception.addSuppressed(rawFailure);
            }
            throw exception;
        } catch (RuntimeException exception) {
            close();
            throw exception;
        }
    }

    public boolean isReady() {
        WindowsRawInputService current = rawInput;
        return current != null ? current.isRunning() : legacyRegistered && GlobalScreen.isNativeHookRegistered();
    }

    public String inputSourceName() {
        return rawInput != null ? "Raw Input" : "JNativeHook";
    }

    /**
     * 关闭 jnativehook 默认日志，减少控制台噪音。
     */
    private void disableJNativeHookLogging() {
        Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
        for (Handler handler : logger.getHandlers()) {
            logger.removeHandler(handler);
        }
        logger.setLevel(Level.OFF);
        logger.setUseParentHandlers(false);
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent nativeEvent) {
        eventCallback.run();
    }

    /**
     * 仅在按下时记一次，避免 click/release 叠加造成重复统计。
     */
    @Override
    public void nativeMousePressed(NativeMouseEvent nativeEvent) {
        eventCallback.run();
    }

    @Override
    public void nativeMouseClicked(NativeMouseEvent nativeEvent) {
    }

    @Override
    public void nativeMouseReleased(NativeMouseEvent nativeEvent) {
    }

    @Override
    public void nativeMouseDragged(NativeMouseEvent nativeEvent) {
    }

    @Override
    public void nativeMouseMoved(NativeMouseEvent nativeEvent) {
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent nativeEvent) {
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent nativeEvent) {
    }

    /**
     * 关闭全局钩子（幂等）。
     */
    @Override
    public synchronized void close() {
        if (!started.get()) {
            return;
        }
        WindowsRawInputService current = rawInput;
        rawInput = null;
        if (current != null) {
            current.close();
        }
        try {
            if (!legacyRegistered) {
                return;
            }
            GlobalScreen.removeNativeKeyListener(this);
            GlobalScreen.removeNativeMouseListener(this);
            if (GlobalScreen.isNativeHookRegistered()) {
                GlobalScreen.unregisterNativeHook();
            }
        } catch (NativeHookException ignored) {
        } finally {
            legacyRegistered = false;
            started.set(false);
        }
    }
}
