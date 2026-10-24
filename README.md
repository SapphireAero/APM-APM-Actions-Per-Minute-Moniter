# APM Monitor

基于需求文档实现的 JavaFX 桌面版 APM 监控工具。Windows 优先使用后台 Raw Input，注册失败时回退到 JNativeHook。

## 功能

- 全局键盘按下、鼠标按下监听
- 指定窗口过滤（支持窗口标题关键字 + 一键捕获当前窗口）
- 实时 APM、平均 APM、峰值 APM
- 折线图趋势（5s/10s/30s 聚合）
- 统计时间范围切换（全程/1分钟/5分钟/15分钟/30分钟）

暂停前会保存尚未采样的操作；重置会同时清空历史与全程累计统计，并隔离仍在处理的旧输入和旧采样。
“全程”的均值与峰值独立累计，直到重置。趋势图最多保留最近86400个采样点（连续采样约24小时），达到上限后会显示“仅显示最近记录”。
最近时间范围采用左开右闭区间，例如最近1分钟只纳入 `(当前时间−60秒, 当前时间]` 内的采样记录。

## 回归验证

使用 JDK 17 或更高版本执行 `mvn test`。测试覆盖暂停计数、重置/恢复期间的并发输入与采样、时间范围边界、历史淘汰后的全程统计，以及暂停后的窗口过滤界面状态。
界面状态测试只初始化 JavaFX 控件，不显示窗口。Windows 原生测试会实际注册、注销后台 Raw Input 消息窗口，不显示窗口、不抢焦点、不注入输入。
真实游戏中的键鼠接收和窗口切换效果仍需按 `人工复测.md` 检查。

## 全屏游戏兼容性

Windows 输入通道使用 `RIDEV_INPUTSINK` 注册键盘和鼠标，允许应用在后台接收原始输入；不会禁用系统常规键鼠消息，也不会捕获或锁定鼠标。
仅统计键盘按下、鼠标按钮按下；松开、鼠标移动和滚轮不计数。Raw Input 与 JNativeHook 不同时运行，避免重复计数。
开始监控后，“监控运行状态”会显示实际输入通道，例如“监听中（Raw Input）”。

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
│  │  ├─ NativeHookService.java             # 输入通道选择与生命周期
│  │  ├─ WindowsRawInputService.java        # Windows 后台输入消息窗口
│  │  └─ RawInputDecoder.java               # 键鼠按下事件计数
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
- `com.apm.monitor.core.hook`：Windows 后台 Raw Input 与 JNativeHook 回退
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
