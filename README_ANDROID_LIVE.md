# 跑得快分析器 · Android Live v0.9.0 (source)

这是 Android 正式 Live 工程，不再是“选 MP4 后分析”的 A0 主流程。

## 用户流程

1. 打开 App，配置自动识别、Overlay、Advisor、搜索预算、Top-K。
2. 首次使用授予“显示在其他应用上层”权限。
3. 点击“开始分析”，接受 Android 系统的屏幕录制/共享授权。
4. 切回跑得快游戏。
5. 前台服务持续采集屏幕；检测到 16 张初始手牌后自动创建 Tracker。
6. 自动跟踪本家/对手动作和 PASS；轮到本家时异步运行 SO-ISMCTS。
7. Overlay 显示 Top-K 搜索建议；对手回合自动缩为状态提示。
8. 一局结束后自动等待下一局。用户点击“结束分析”或 Overlay“停止”才终止服务。

## 运行链

MediaProjection -> ImageReader -> FrameNormalizer(1296x772)
-> StableFrameGate -> UI/Hand/Table Vision
-> LiveEventDecoder -> GameTracker
-> hidden-world history filter -> SO-ISMCTS worker
-> stale-result guard -> OverlayController

## 关键设计

- Vision/Tracker 主循环和 Advisor 分线程，搜索不会阻塞采集。
- Advisor 返回时按 stateKey 校验；过期结果直接丢弃。
- Decoder 事件先交给 Tracker 验证，只有 ACCEPT 后才确认本地手牌变化。
- 结果界面出现但 Tracker 未闭环时，本局进入 quarantine，然后自动等下一局，避免污染后续状态。
- Overlay 固定在左上角，避开当前校准的手牌/桌面牌/UI 识别 ROI。
- Audit 默认只保存文本证据到应用私有 external-files/audit，不上传数据。

## 当前工程范围

- 目标：两人、经典 16 张好友房，和现有 Windows/Python Golden 数据一致的 rank-only 分析链。
- RuleEngine/Tracker 已通过 4 局 Golden transcript 最终状态回归。
- Android SO-ISMCTS 已对 24 个 Golden 本家决策节点完成合法性/隐藏世界采样验证。
- Vision 模板来自已经验证过的 Windows pipeline，但 Android MediaProjection 的真实设备画面尚未做 APK 实机验收；这是构建后的第一项验收，不应假装已验证。

## 验证

源码静态检查：

    python tools/validate_source.py

纯 Java 核心（无需 Android SDK）在开发环境已验证：

- RuleEngine + GameTracker: Golden 4/4 PASS
- SO-ISMCTS: Golden decision nodes 24/24 PASS

## 构建前仍需完成的门槛

1. 在 Android SDK/Gradle 环境实际 compileDebugJavaWithJavac / testDebugUnitTest。
2. 修复任何 Android API/依赖编译问题（如果存在）。
3. 构建 debug APK。
4. 真机首次运行验证 MediaProjection 分辨率/旋转与 Overlay 权限。
5. 使用同一 Golden 录像在 Android 真机录屏回放/实机房间做端到端验收。

只有完成上述门槛后，APK 才应作为可安装验收版本交付。
