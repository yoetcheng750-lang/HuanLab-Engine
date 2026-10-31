# YOET V5 交付报告

> 本报告只记录实际执行结果。没有真机证据的项目一律标记 UNVERIFIED，
> 不用编译通过代替运行验证，不用 UI 显示代替底层数据。

## 10.1 项目基本信息

| 项目 | 值 | 来源 |
|---|---|---|
| 项目名称 | YOET（HumanLab Engine） | 工程 |
| 版本 | V5（基于 V4 `cf967b0`） | Git |
| applicationId | `com.huanlab.engine` | `app/build.gradle.kts` |
| 设备 Android 版本 | **UNVERIFIED** | 设备通道当前不可用（`DEVICE_CHANNEL_UNAVAILABLE`） |
| 设备 API Level | **UNVERIFIED** | 同上 |
| 设备型号 | **UNVERIFIED** | 同上（历史记录为 OPPO K15 Pro，未在本轮实测） |
| compileSdk | 35 | `app/build.gradle.kts` |
| targetSdk | 35 | 同上 |
| minSdk | 26 | 同上 |
| Kotlin | 2.0.21 | `build.gradle.kts` |
| AGP | 8.7.3 | 同上 |
| Gradle | 8.9 | `gradle/wrapper/gradle-wrapper.properties` |
| JDK | 17（本地 17.0.20.1；CI temurin 17） | 实测 |
| Compose BOM | 2025.09.01 | `app/build.gradle.kts` |
| Shizuku SDK | 13.1.5 | 同上 |

### 设备环境说明

本轮执行 `adb-shell getprop` 返回 `DEVICE_CHANNEL_UNAVAILABLE`，
容器内 `/system/build.prop` 不可读、无 `getprop` 可执行文件。
按任务书要求，**不推测设备版本**，全部标记 UNVERIFIED。

## 10.2 模块开发结果

### System Health（P0）

- 新增文件：
  - `service/health/HealthModels.kt`（状态枚举 + 聚合）
  - `service/health/SystemHealthChecker.kt`（工程/Shizuku/权限/Thermal 检查）
  - `service/health/DiagnosticReport.kt`（白名单诊断报告）
  - `ui/HealthViewModel.kt`
  - `ui/screen/HealthScreen.kt`
  - `assets/core_baseline.sha256`（25 个核心文件基线，构建期固化）
  - `res/xml/file_paths.xml` + Manifest FileProvider
- 修改的非核心文件：`ui/MainActivity.kt`（新增「自检」页签）、`AndroidManifest.xml`
- 真实数据接口：`PackageManager`（签名/版本）、`Shizuku` 状态、`AppOpsManager`、`Settings.canDrawOverlays`、`PowerManager.getCurrentThermalStatus`
- 降级能力：未授权 → `DEGRADED`；API 不支持 → `UNSUPPORTED`；无法确认 → `UNKNOWN`
- 未解决问题：核心文件运行时无法自我校验，仅展示构建期固化基线数量，并以 CI 的 `sha256sum -c` 为准

### Performance Overlay（P1）

- 新增文件：
  - `service/overlay/OverlayService.kt`（前台服务 + WindowManager + 通知关闭）
  - `service/overlay/OverlayController.kt`（权限检查/启停/运行态）
  - `service/overlay/OverlayOwner.kt`（ComposeView 宿主 lifecycle）
  - `service/overlay/OverlayPreferences.kt`（位置/透明度/展开持久化）
  - `ui/overlay/OverlayContent.kt`（悬浮窗 UI）
- Manifest：`SYSTEM_ALERT_WINDOW`、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_SPECIAL_USE`，服务声明 `foregroundServiceType="specialUse"` + subtype `performance_overlay`
- 实现：拖动、左右吸附、点按展开/收起、双击切换透明度（0.88/0.65/0.45）、位置与透明度持久化、旋转后重新钳制到屏内
- 关闭流程：释放采样引用 → 移除窗口 → 销毁 lifecycle → 停通知 → `stopSelf`；通知栏提供「关闭悬浮窗」动作
- 未解决问题：未在真机验证拖动/吸附/旋转表现（UNVERIFIED）

### FPS Intelligence（P1）

- 新增文件：
  - `service/fps/FpsModels.kt`
  - `service/fps/SurfaceFlingerParser.kt`（latency 解析、窗口过滤、FPS 计算）
  - `service/fps/LayerResolver.kt`（Layer 唯一性判定）
  - `service/fps/FpsEngine.kt`（Shizuku 命令调度与降级）
  - `app/src/test/java/.../SurfaceFlingerParserTest.kt`（12 个用例）
- 数据源：`dumpsys SurfaceFlinger --list` + `--latency <layer>`，经 Shizuku 一次性授权链路执行
- 降级：Layer 不唯一 → `UNKNOWN`；ROM 封锁 → `UNSUPPORTED`；有效帧 < 10 → `UNKNOWN`；Shizuku 不可用 → `FAILED`
- 安全：不使用 `--timestats` 全局开关（无法确认可安全恢复原始状态，按任务书要求不启用）
- 未解决问题：`gfxinfo` 备用路径未接入；真机 Layer 名称格式未验证（UNVERIFIED）

### SamplingCoordinator（阶段 C）

- 新增：`service/sampling/SamplingCoordinator.kt`（单一采样循环 + 引用计数 + 共享 StateFlow）
- 修改：`ui/HuanLabViewModel.kt` 改为订阅协调器，首页/监控/悬浮窗共用同一份 CPU、内存、热状态、刷新率、前台应用数据
- 前台识别每 3 个采样周期（约 6 秒）刷新一次，避免高频 UsageStats 查询

## 10.3 核心文件保护结果

| 项目 | 结果 |
|---|---|
| 核心文件总数 | 25 |
| 原始基线是否可信 | **可信**：基线由 `2d4c5ad` 时期生成，且用 `git show HEAD:<file> \| sha256sum` 交叉验证（3 个样本 git blob == 基线 == 磁盘） |
| 开发前检查 | 25/25 OK |
| 开发中检查（阶段 B/C/D/E 后） | 25/25 OK |
| 开发后检查 | 25/25 OK |
| 发生变化的文件数量 | **0** |
| 最终结论 | 核心文件未修改、未重命名、未移动、未删除 |

证据命令：`sha256sum -c app/src/main/assets/core_baseline.sha256`（CI 中同步骤 `Core baseline verification` 已执行并通过）。

## 10.4 编译与 CI 结果

| 项目 | 结果 |
|---|---|
| `:app:compileDebugKotlin` | ✅ 本地 `BUILD SUCCESSFUL` |
| 本地 `:app:assembleDebug` | ❌ `AAPT2 ... Daemon startup failed`（容器 aarch64 无法执行 x86_64 aapt2，非代码问题） |
| 本地 `:app:testDebugUnitTest` | ❌ `bundleDebugClassesToRuntimeJar` 依赖资源任务，本地无法执行（环境限制） |
| 云端 `assembleDebug` | ✅ success |
| 云端 `testDebugUnitTest` | ✅ success（12 tests，0 failure） |
| 云端 `Core baseline verification` | ✅ success（25/25） |
| Git Commit（代码） | `daeebf4`（报告提交后 SHA 会前进） |
| Commit（模块实现） | `0764c0d feat(v5): system health, sampling coordinator, performance overlay, FPS intelligence` |
| GitHub Actions | ✅ run **41188720998** success |
| Actions 链接 | https://github.com/yoetcheng750-lang/HuanLab-Engine/actions/runs/41188720998 |
| APK 输出位置 | `/storage/emulated/0/Log storage/YOET-v5-debug.apk` |
| APK sha256 | `05cfc0f179e1e6136dc5d68811498b37f3a88d207e0ca0dbcb42e1c4d51f52df` |
| APK 大小 | 11,533,310 字节（约 11MB） |

APK 内容实证（解包检查）：

- `AndroidManifest.xml`：`com.huanlab.engine` ×8、`SYSTEM_ALERT_WINDOW` ×1、`FOREGROUND_SERVICE_SPECIAL_USE` ×1、`OverlayService` ×1、`fileprovider` ×1、`YoeApplication` ×1
- dex：`SystemHealthChecker`、`SamplingCoordinator`、`OverlayService`、`FpsEngine`、`SurfaceFlingerParser`、`LayerResolver`、`HealthScreen`、`DiagnosticReport` 全部命中

## 10.5 验证状态

| 功能 | 状态 | 证据 |
|---|---|---|
| applicationId 保持 `com.huanlab.engine` | VERIFIED | gradle 文件 + APK Manifest |
| 25 个核心文件 SHA-256 | VERIFIED | 本地 + CI 双次 `sha256sum -c` |
| Kotlin 编译 | VERIFIED | 本地 BUILD SUCCESSFUL |
| APK 构建 | VERIFIED | CI success + APK 解包检查 |
| FPS 解析器单元测试（12 例） | VERIFIED | CI `testDebugUnitTest` success |
| 核心基线 CI 校验 | VERIFIED | CI step success |
| System Health 页面渲染 | UNVERIFIED | 无真机/模拟器截图验证 |
| Shizuku 五态识别（安装/运行/授权/Binder/UID） | UNVERIFIED | 依赖真机 Shizuku |
| 诊断报告分享 | UNVERIFIED | 依赖真机 FileProvider 行为 |
| 悬浮窗启动/拖动/吸附/关闭 | UNVERIFIED | 依赖真机 WindowManager |
| 前台服务（specialUse）在 Android 14+ 的表现 | UNVERIFIED | 依赖真机系统版本 |
| FPS 真实采集（SurfaceFlinger） | UNVERIFIED | 依赖真机 ROM 与 Shizuku |
| 设备 Android 版本/API/型号 | UNVERIFIED | `DEVICE_CHANNEL_UNAVAILABLE` |
| 跨窗口 CPU 采样在真机上的跳动 | UNVERIFIED | 依赖真机运行 |

## 10.6 最终结论

1. **YOET V5 是否成功构建**：是。CI 全绿（构建、12 个单元测试、核心基线校验）。
2. **V4 核心文件是否完整**：是，25/25 SHA-256 一致，0 变更。
3. **三个模块完成程度**：
   - System Health：代码完成（检查项 + 诊断导出 + 自检页签），真机待验；
   - Performance Overlay：代码完成（前台服务 + 拖动/吸附/透明度/持久化/完整关闭），真机待验；
   - FPS Intelligence：代码 + 解析器单元测试完成（Layer 唯一性、有效帧过滤、降级），真机采集待验。
4. **是否通过 GitHub Actions**：是，run 41188720998。
5. **可以立即真机测试的功能**：自检页各项状态、诊断报告导出、悬浮窗启停与拖动、FPS 在 Shizuku 就绪下的采集结果。
6. **仍有风险的功能**：悬浮窗在 ColorOS 上的后台限制与前台服务类型；SurfaceFlinger Layer 命名差异；`specialUse` 前台服务在 Android 14+ 的合规性。
7. **需要手动授权的权限**：
   - 悬浮窗（设置 → 特殊权限 → 显示在其他应用上层）
   - 使用情况访问（设置 → 特殊权限 → 使用情况访问）
   - 所有文件访问（崩溃日志写入根目录时）
   - Shizuku 授权
8. **下一阶段建议**：
   - 先在 OPPO K15 Pro 上验证悬浮窗与 FPS 两模块，再决定是否需要 `gfxinfo` 备用路径；
   - 性能历史记录（P1）与温控策略（P2）建议在真机通过后启动；
   - 若 ColorOS 限制 SurfaceFlinger，需要评估 `gfxinfo framestats` 作为明确标注的「应用 UI 帧率」降级项。

---

生成时间：2026-10-31
生成者：YOET V5 工程实施流程
