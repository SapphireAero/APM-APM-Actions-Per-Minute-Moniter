package com.apm.monitor.core.hook;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseInputListener;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Handler;
import java.util.logging.Logger;

/**
 * 全局输入钩子服务。
 * <p>
 * 封装 jnativehook 的注册、监听和反注册流程，
 * 对外只暴露“启动/关闭 + 事件回调”两个概念。
 */
public class NativeHookService implements NativeKeyListener, NativeMouseInputListener, AutoCloseable {
    private final Runnable eventCallback;
    private final AtomicBoolean started = new AtomicBoolean(false);

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
    public void start() throws NativeHookException {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        try {
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(this);
            GlobalScreen.addNativeMouseListener(this);
        } catch (NativeHookException exception) {
            close();
            throw exception;
        } catch (RuntimeException exception) {
            close();
            throw exception;
        }
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
    public void close() {
        if (!started.get()) {
            return;
        }
        try {
            GlobalScreen.removeNativeKeyListener(this);
            GlobalScreen.removeNativeMouseListener(this);
            if (GlobalScreen.isNativeHookRegistered()) {
                GlobalScreen.unregisterNativeHook();
            }
        } catch (NativeHookException ignored) {
        } finally {
            started.set(false);
        }
    }
}
