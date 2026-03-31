package com.apm.monitor.core.service;

import com.apm.monitor.core.api.ApmBackend;
import com.apm.monitor.core.engine.ApmMonitorEngine;
import com.apm.monitor.core.hook.NativeHookService;
import com.apm.monitor.core.window.WindowService;
import com.apm.monitor.model.ApmSnapshot;
import com.github.kwhat.jnativehook.NativeHookException;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * APM 后端服务实现。
 * <p>
 * 该类负责组合三个核心能力：
 * 1) 全局输入事件采集（{@link NativeHookService}）；
 * 2) APM 统计引擎（{@link ApmMonitorEngine}）；
 * 3) 对控制层暴露统一的门面接口（{@link ApmBackend}）。
 */
public class ApmBackendService implements ApmBackend {
    private final ApmMonitorEngine engine;
    private final NativeHookService hookService;

    /**
     * 钩子启动是否成功。
     * 使用 volatile 保障 UI 线程读取可见性。
     */
    private volatile boolean hookReady;

    /**
     * 服务是否已经关闭。关闭后对外方法均应安全降级（no-op）。
     */
    private volatile boolean closed;

    /**
     * @param windowService    前台窗口服务
     * @param snapshotConsumer 引擎快照回调（通常在 UI 线程消费）
     */
    public ApmBackendService(WindowService windowService, Consumer<ApmSnapshot> snapshotConsumer) {
        Objects.requireNonNull(windowService, "windowService");
        Objects.requireNonNull(snapshotConsumer, "snapshotConsumer");
        this.engine = new ApmMonitorEngine(windowService, snapshotConsumer);
        this.hookService = new NativeHookService(engine::onInputEvent);
    }

    /**
     * 注册全局输入钩子。
     */
    @Override
    public void startHook() throws NativeHookException {
        if (closed) {
            throw new IllegalStateException("ApmBackendService is closed");
        }
        hookService.start();
        hookReady = true;
    }

    @Override
    public boolean isHookReady() {
        return hookReady;
    }

    @Override
    public void setTargetWindowTitle(String title) {
        if (closed) {
            return;
        }
        engine.setTargetWindowTitle(title);
    }

    @Override
    public void setFilterByWindow(boolean enabled) {
        if (closed) {
            return;
        }
        engine.setFilterByWindow(enabled);
    }

    @Override
    public void startMonitoring() {
        if (closed) {
            return;
        }
        engine.start();
    }

    @Override
    public void pauseMonitoring() {
        if (closed) {
            return;
        }
        engine.pause();
    }

    @Override
    public boolean isMonitoring() {
        return !closed && engine.isMonitoring();
    }

    @Override
    public void resetMonitoring() {
        if (closed) {
            return;
        }
        engine.reset();
    }

    @Override
    public String captureCurrentForegroundWindow() {
        if (closed) {
            return "未知窗口";
        }
        return engine.getCurrentForegroundWindow();
    }

    /**
     * 关闭资源（幂等）。
     * <p>
     * 关闭顺序：
     * 1) 标记 closed，阻断后续调用；
     * 2) 停止钩子采集；
     * 3) 停止统计引擎。
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        hookReady = false;
        hookService.close();
        engine.close();
    }
}
