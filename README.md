# APM Monitor

基于需求文档实现的 JavaFX + JNativeHook 桌面版 APM 监控工具。

## 功能

- 全局键盘按下、鼠标按下监听
- 指定窗口过滤（支持窗口标题关键字 + 一键捕获当前窗口）
- 实时 APM、平均 APM、峰值 APM
- 折线图趋势（5s/10s/30s 聚合）
- 统计时间范围切换（全程/1分钟/5分钟/15分钟/30分钟）

## 项目结构

```text
src/main/java/com/apm/monitor
├─ ApmFxApplication.java                    # JavaFX 主应用生命周期入口（start/stop）
├─ FrontendMainLauncher.java                # 推荐启动入口（main）
├─ bootstrap
│  ├─ ApmApplicationAssembler.java          # 应用装配器：组装 View/Controller/Backend
├─ core
│  ├─ api
│  │  └─ ApmBackend.java                    # 后端门面接口定义
│  ├─ service
│  │  └─ ApmBackendService.java             # 后端门面实现，协调引擎/钩子/窗口服务
│  ├─ engine
│  │  └─ ApmMonitorEngine.java              # 实时 APM 统计引擎（采样、过滤、快照）
│  ├─ stats
│  │  └─ ApmStatsCalculator.java            # 统计计算工具（平均、峰值、聚合）
│  ├─ hook
│  │  └─ NativeHookService.java             # 全局键鼠钩子封装（JNativeHook）
│  └─ window
│     ├─ WindowService.java                 # 前台窗口识别抽象接口
│     └─ WindowsForegroundWindowService.java# Windows 平台前台窗口识别实现
├─ model
│  ├─ ApmPoint.java                         # 单点 APM 数据模型（时间戳 + 数值）
│  ├─ ApmSnapshot.java                      # 引擎快照模型（供 UI 刷新）
│  ├─ ChartStep.java                        # 图表聚合步长枚举
│  └─ StatsRange.java                       # 统计范围枚举
└─ ui
   ├─ DashboardView.java                    # 页面视图构建与组件提供
   ├─ DashboardController.java              # 事件绑定与 UI 状态控制
   └─ style
      └─ DashboardStyleTokens.java          # 样式令牌常量（颜色、卡片样式等）
```

- `com.apm.monitor`：应用入口层（JavaFX 生命周期与启动类）
- `com.apm.monitor.bootstrap`：装配层（构建 View/Controller/Backend）
- `com.apm.monitor.core.api`：后端门面接口
- `com.apm.monitor.core.service`：后端门面实现与编排
- `com.apm.monitor.core.engine`：实时 APM 统计引擎
- `com.apm.monitor.core.stats`：统计工具（均值、峰值、聚合）
- `com.apm.monitor.core.hook`：全局键鼠监听封装（JNativeHook）
- `com.apm.monitor.core.window`：前台窗口识别抽象与 Windows 实现
- `com.apm.monitor.model`：领域数据模型
- `com.apm.monitor.ui`：UI 层（页面组件与交互控制）
- `com.apm.monitor.ui.style`：样式令牌与视觉常量

## 运行环境

- 操作系统：Windows 10/11（x64，当前窗口识别依赖 JNA User32）
- JDK 17
- Maven：3.9+
- 构建编码：UTF-8
- JavaFX：`javafx-controls 17.0.10`
- 全局输入监听：`jnativehook 2.2.2`
- Windows API 调用：`jna-platform 5.14.0`
- 首次构建需要可访问 Maven 仓库（用于下载依赖）
