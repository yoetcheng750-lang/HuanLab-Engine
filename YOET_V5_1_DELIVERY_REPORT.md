# YOET V5.1 交付报告

> 只记录真实执行与真实探测结果。无真机证据的能力一律标记 UNVERIFIED；
> 被系统限制的节点标记 DEGRADED / UNSUPPORTED，不用假数据填充。

## 12.1 真实设备信息

| 项目 | 值 | 来源 |
|---|---|---|
| 设备 Android 版本 | **16** | `getprop ro.build.version.release`（设备 shell） |
| API Level | **36** | `getprop ro.build.version.sdk` |
| 安全补丁 | 2026-09-01 | `getprop ro.build.version.security_patch` |
| 设备型号 | OPPO **PMH110** | `getprop ro.product.model` |
| 设备代号 | OP61E9L1 | `getprop ro.product.device` |
| 系统版本 | PMH110_16.0.10.600(CN01) | `getprop ro.build.display.id` |
| Shizuku 执行 UID | **2000（shell）** | `adb-shell id` → `uid=2000(shell)` |
| Shizuku 身份性质 | **非 Root** | 未出现 uid=0；未执行 su |
| CPU 拓扑 | 8 核（cpu0-7），3 个 cpufreq policy | `/sys/devices/system/cpu/` + `cpufreq/` |

## 12.2 CPU 频率节点探测结果

探测命令：`cat <node>`（经设备 shell，只读）。真实结果：

| 节点 | 是否存在 | Shell 是否可读 | 原始值 | 单位 | 结果 |
|---|---|---|---|---|---|
| `/sys/devices/system/cpu/cpufreq/policy0/related_cpus` | 是 | 是 | `0 1 2 3` | cpu 列表 | PASS |
| `policy0/cpuinfo_cur_freq` | 是 | **否（Permission denied）** | — | kHz | DEGRADED |
| `policy0/scaling_cur_freq` | 是 | 是 | `1800000` | kHz | PASS（内核报告） |
| `policy0/cpuinfo_max_freq` | 是 | 是 | `2200000` | kHz | 仅上限，非当前频率 |
| `policy0/scaling_max_freq` | 是 | **否（Permission denied）** | — | kHz | DEGRADED |
| `policy0/scaling_governor` | 是 | **否（Permission denied）** | — | — | DEGRADED |
| `policy4/related_cpus` | 是 | 是 | `4 5 6` | cpu 列表 | PASS |
| `policy4/cpuinfo_cur_freq` | 是 | **否（Permission denied）** | — | kHz | DEGRADED |
| `policy4/scaling_cur_freq` | 是 | 是 | `2600000` | kHz | PASS（内核报告） |
| `policy4/cpuinfo_max_freq` | 是 | 是 | `3200000` | kHz | 仅上限 |
| `policy7/related_cpus` | 是 | 是 | `7` | cpu 列表 | PASS |
| `policy7/cpuinfo_cur_freq` | 是 | **否（Permission denied）** | — | kHz | DEGRADED |
| `policy7/scaling_cur_freq` | 是 | 是 | `1500000` | kHz | PASS（内核报告） |
| `policy7/cpuinfo_max_freq` | 是 | 是 | `3400000` | kHz | 仅上限 |
| `/sys/devices/system/cpu/online` | 是 | 是 | `0-7` | — | PASS |

**结论**：`cpuinfo_cur_freq`（硬件反馈）被 ColorOS 的 SELinux 限制；只有 `scaling_cur_freq` 可读。
UI 已按任务书要求标注为 **「CPU 频率（内核报告）」**，不声称是硬件瞬时频率。

## 12.3 温度节点探测结果

`/sys/class/thermal/` 下存在 **73 个** thermal_zone（thermal_zone0 … thermal_zone72）。

| Zone | 原始 type | 节点是否存在 | 是否可读 | 原始 temp | 单位 | 最终结果 |
|---|---|---|---|---|---|---|
| thermal_zone0 | 不可读 | 是 | **否（Permission denied）** | — | 标准 ABI 毫摄氏度 | DEGRADED |
| thermal_zone1 | 不可读 | 是 | **否（Permission denied）** | — | — | DEGRADED |
| thermal_zone2 | 不可读 | 是 | **否（Permission denied）** | — | — | DEGRADED |
| thermal_zone3-5 | 不可读 | 是 | **否（Permission denied）** | — | — | DEGRADED |
| thermal_zone10 / 20 / 40 / 60 / 72 | 不可读 | 是 | **否（Permission denied）** | — | — | DEGRADED |
| `/sys/devices/virtual/thermal` | 不可读 | 是 | **否（Permission denied）** | — | — | DEGRADED |
| `/sys/class/hwmon/` | 不存在 | 否 | — | — | — | UNSUPPORTED |
| `dumpsys battery`（BatteryManager） | battery | 是 | **是** | `359` | 0.1℃ | **PASS：35.9℃** |

**结论**：ColorOS 16（API 36）在 shell uid 2000 下封锁了全部 thermal_zone 读取；
CPU / GPU / SoC 温度无法获取，UI 显示「—」并标注「系统权限限制」。
电池温度通过 Android 标准 `BatteryManager` 真实读取成功（35.9℃），来源标注 `BatteryManager`。

## 12.4 前台服务修复结果

| 检查项 | 结果 |
|---|---|
| `FOREGROUND_SERVICE` | ✅ 已声明 |
| `FOREGROUND_SERVICE_SPECIAL_USE` | ✅ 已声明 |
| `OverlayService` | ✅ `com.huanlab.engine.service.overlay.OverlayService`，`exported=false` |
| `foregroundServiceType` | ✅ APK 解包实测 `0x40000000` = `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` |
| `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` | ✅ `performance_overlay: user-visible performance monitoring overlay started by the user` |
| Manifest merger | ✅ 通过（Gradle 构建 + APK 解包确认） |
| APK Manifest | ✅ `apkanalyzer manifest print` 实测 |
| 启动代码 | ✅ 改为 `ServiceCompat.startForeground(...)`，API 34+ 传 SPECIAL_USE 类型 |
| 停止代码 | ✅ `ServiceCompat.stopForeground(..., STOP_FOREGROUND_REMOVE)` |
| System Health 检查 | ✅ 改为真实检查 `ServiceInfo.foregroundServiceType` + `checkSelfPermission`，不再无条件 DEGRADED |
| 真机启动 | **UNVERIFIED**（未在真机点击启动悬浮窗） |
| 真机关闭 | **UNVERIFIED** |

## 12.5 版本信息

| 项目 | 值 | 验证方式 |
|---|---|---|
| applicationId | `com.huanlab.engine` | gradle + APK Manifest |
| versionCode | **2** | `apkanalyzer manifest version-code` → `2` |
| versionName | **1.1.0** | `apkanalyzer manifest version-name` → `1.1.0` |

## 12.6 构建结果

| 项目 | 结果 |
|---|---|
| `:app:compileDebugKotlin` | ✅ 本地 BUILD SUCCESSFUL |
| 本地 `assembleDebug` | ❌ `AAPT2 ... Daemon startup failed`（aarch64 无法执行 x86_64 aapt2，环境限制） |
| 云端 `testDebugUnitTest` | ✅ **47 tests, 0 failure**（含 12 个 FPS + 17 个 CPU 频率 + 18 个温度） |
| 云端 `assembleDebug` | ✅ success |
| 云端 Core baseline verification | ✅ 25/25 |
| GitHub Actions | ✅ run **41190697480** success |
| Actions 链接 | https://github.com/yoetcheng750-lang/HuanLab-Engine/actions/runs/41190697480 |
| Commit SHA（最终代码） | `118d546`（模块实现 `5216ce8`，测试修正 `118d546`） |
| APK 文件大小 | 11,118,135 字节（约 11MB） |
| APK SHA-256 | `487ddb815d8917341b265ace05d91c9ae6372da6f0155bc4260166be1724f7e4` |
| APK 下载位置 | `/storage/emulated/0/Log storage/YOET-v5.1-debug.apk` |

APK dex 实证：`CpuFrequencyMonitor`、`CpuFrequencyParser`、`ThermalMonitor`、`ThermalParser`、`ProbeStatus`、`SamplingCoordinator` 均已打包。

## 12.7 最终验证状态

### VERIFIED（有实际证据）

- applicationId `com.huanlab.engine`（gradle + APK）
- versionCode=2 / versionName=1.1.0（apkanalyzer 实测）
- 25 个核心文件 SHA-256（本地 + CI 双次校验，0 变更）
- Kotlin 编译成功（本地）
- APK 构建成功（CI）
- 47 个单元测试通过（CI）
- CPU 频率节点真实探测结果（3 个 policy，scaling_cur_freq 可读，cpuinfo_cur_freq 被拒）
- Thermal zone 真实探测结果（73 个 zone 全部被拒）
- 电池温度真实读取（BatteryManager 35.9℃）
- 前台服务 Manifest 声明与 APK 解包结果
- `ServiceCompat.startForeground` 代码实现

### UNVERIFIED（需真机验证）

- 悬浮窗启动/拖动/吸附/关闭
- 前台通知显示与关闭按钮
- System Health 页面在真机上的渲染与 FGS 检查结果
- CPU 频率在 UI 上的实时跳动
- 温度卡片在真机上的显示（预计 CPU/GPU 显示「—」，Battery 显示真实值）

### DEGRADED（环境限制，已如实处理）

- CPU `cpuinfo_cur_freq`：SELinux 拒绝（shell uid 2000）
- CPU/GPU/SoC 温度：thermal_zone 全部 Permission denied
- `scaling_max_freq` / `scaling_governor`：Permission denied

### UNSUPPORTED

- `/sys/class/hwmon`：设备未提供

### FAILED

- 无（本轮没有出现明确失败的实现项）

### BLOCKED

- 无

---

## 关键说明

1. **未使用 Root**：全部探测经 shell uid 2000，未执行 `su -c`，未修改 SELinux，未写任何 sysfs 节点。
2. **未伪造数据**：CPU 频率只展示可读的 `scaling_cur_freq` 并标注内核报告；温度不可读处显示「—」并给出原因。
3. **未降低判断标准**：System Health 的 FGS 检查改为读取真实 `ServiceInfo` 与权限授予状态，没有为了让界面变绿而直接返回 PASS。
4. **版本号与 APK 一致**：以 `apkanalyzer` 解包结果为准，不是只改 Gradle 文件。

生成时间：2026-10-31
