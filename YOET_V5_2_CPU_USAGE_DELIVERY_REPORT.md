# YOET V5.2 · CPU Usage Intelligence 交付报告

> 只记录真机实际命令输出。未验证项标记 UNVERIFIED，不可用项标记 UNSUPPORTED / DEGRADED，
> Load Average 不参与 CPU 百分比计算。

## A. 工程保护

| 项目 | 值 |
|---|---|
| applicationId | `com.huanlab.engine` |
| 开发起点 Commit | `f4f18fb`（V5.1 报告） |
| 本次代码 Commit | `a09a6b9` |
| 核心文件基线 | `app/src/main/assets/core_baseline.sha256`（25 项） |
| 开发前 SHA-256 | 25/25 OK |
| 开发后 SHA-256 | 25/25 OK |
| 核心文件变化 | **0** |
| versionCode / versionName | **3 / 1.2.0**（APK 解包实测） |

核心保护命令与结果：

```
sha256sum -c app/src/main/assets/core_baseline.sha256  → 25 个 OK，0 个 FAILED
CI step "Core baseline verification"                    → success
```

## B. 数据源探测（真机，shizuku shell uid 2000）

### 执行身份

```
id -u → 2000
id    → uid=2000(shell) ... context=u:r:shell:s0
```

### B.1 dumpsys cpuinfo — PASS

真实输出（第一次探测，18:08 左右）：

```
Load: 24.91 / 26.01 / 25.49
CPU usage from 13477ms to 6413ms ago (2026-10-31 18:08:24.661 to 2026-10-31 18:08:31.726) with 99% awake:
  63% 24995/com.google.android.webview:... 51% user + 12% kernel ...
  ...
65% TOTAL: 31% user + 26% kernel + 1% iowait + 5.3% irq + 0.6% softirq
```

| 项目 | 值 |
|---|---|
| 命令是否存在 | 是 |
| 退出码 | 0 |
| 是否返回数据 | 是（262,206 字节 / 4582 行） |
| TOTAL 行数量 | **1**（第 142 行，偏移 9,245 字节，< 32KB 截断上限） |
| user | 31% |
| kernel | 26% |
| iowait | 1% |
| irq | 5.3% |
| softirq | 0.6% |
| 系统总比例 | 65% |
| 采样窗口 | 13,477ms → 6,413ms ago（窗口 7,064ms） |
| 数据是否新鲜 | 是（样本年龄 6,413ms ≤ 15s） |
| 最终状态 | **PASS** |

第二次探测（18:17 左右）得到：

```
CPU usage from 181121ms to 150998ms ago (2026-10-31 18:14:27.299 to 2026-10-31 18:14:57.423)
66% TOTAL: 28% user + 31% kernel + 0.1% iowait + 6.1% irq + 0.7% softirq
```

样本年龄 **150,998ms**（约 151 秒）→ 按 15 秒阈值判定为**过期**，解析器返回 `UNKNOWN("采样已过期")`，
不会把陈旧快照冒充实时值。这验证了任务书强调的「必须检查采样时间」。

### B.2 top — 探测通道被拦截（UNVERIFIED）

```
top --help          → [POLICY_BLOCKED] 不认识的命令不执行：top（exit 126）
top -b -n 1 -m 5    → [POLICY_BLOCKED] 不认识的命令不执行：top（exit 126）
```

这是**当前 DSHA 设备通道的命令白名单限制**，不是 Android 系统拒绝。
应用内通过 Shizuku 执行 `top` 是否成功，**未验证（UNVERIFIED）**。
Provider 已实现 top 解析与降级，但标注为未验证的备用源。

### B.3 /proc/loadavg — PASS（仅独立负载）

```
24.69 25.91 25.47 2/8316 7232
```

可读，解析为 1/5/15 分钟负载 24.69 / 25.91 / 25.47。
**不参与 CPU 百分比计算**，UI 中明确标注「负载不是 CPU 使用率」。

### B.4 额外发现

`/proc/stat` 在 shell 通道实际**可读**（`cpu 732606 45145 ...`），
说明 V5 真机上的 CPU「—」更可能来自 App 进程自身的 SELinux 限制或旧采样窗口逻辑，
而不是 shell 权限。本次不修改 DeviceMonitor，保留 `/proc/stat` 作为补充显示项（标注「内核采样」）。

## C. 最终使用方案

| 项目 | 方案 |
|---|---|
| 主数据源 | **dumpsys cpuinfo**（ActivityManager 系统服务统计） |
| 备用数据源 | Toybox `top -b -n 1 -m 5`（未验证） |
| 降级信息 | `/proc/loadavg`（独立负载，绝不换算为 CPU%） |
| 计算口径 | `valuePercent = user + kernel`（执行态占用） |
| 系统总比例 | 单独保存 `systemTotalPercent`，不与分类值再相加 |
| 采样周期 | 每 2 个采样 tick（约 4 秒） |
| 采样新鲜度 | ≤ 15,000ms 视为 PASS；> 15s → UNKNOWN 并尝试 top |
| 失败降级 | dumpsys 无值 → top → loadavg（仅负载）→ UNKNOWN（UI「—」） |

## D. CPU 真实数据验证

| 问题 | 回答 |
|---|---|
| 是否实际读取到 TOTAL 行 | **是**（两次采样均唯一命中） |
| 是否识别到 user | 是（31% / 28%） |
| 是否识别到 kernel | 是（26% / 31%） |
| 计算结果 | 第一次 **57.0%**（31+26）；第二次 **59.0%**（28+31） |
| 真实采样时间 | 2026-10-31 18:08:31.726（第一次）/ 18:14:57.423（第二次） |
| 数据来源 | `dumpsys cpuinfo` |
| 是否已在 OPPO K15 Pro 真机验证 | **是**（通过 DSHA 设备 shell 通道，uid 2000；YOET 应用内执行时序未验证） |

## E. 构建结果

| 项目 | 结果 |
|---|---|
| `:app:compileDebugKotlin` | ✅ 本地 BUILD SUCCESSFUL |
| 本地 `assembleDebug` | ❌ aapt2 x86_64 无法在 aarch64 执行（环境限制） |
| 云端 `testDebugUnitTest` | ✅ success（新增 dumpsys/top/loadavg 共 34 个用例，全套测试通过） |
| 云端 `assembleDebug` | ✅ success |
| 云端核心基线校验 | ✅ 25/25 |
| GitHub Actions | ✅ run **41191853029** |
| Actions 链接 | https://github.com/yoetcheng750-lang/HuanLab-Engine/actions/runs/41191853029 |
| Commit SHA | `a09a6b9` |
| APK 大小 | 11,138,357 字节 |
| APK SHA-256 | `86e65ac7c57b3e6381aa3d4c27dfbe149f096d8494302a63ba6c83952dbdb228` |
| APK 版本 | versionCode **3** / versionName **1.2.0**（apkanalyzer 实测） |
| APK 包名 | `com.huanlab.engine`（apkanalyzer 实测） |
| 下载位置 | `/storage/emulated/0/Log storage/YOET-v5.2-debug.apk` |

## F. 最终验收状态

| 能力 | 状态 | 证据 |
|---|---|---|
| applicationId | VERIFIED | gradle + APK |
| 版本 3 / 1.2.0 | VERIFIED | apkanalyzer |
| 核心 25 文件 | VERIFIED | 本地 + CI 双次 |
| dumpsys cpuinfo 可读 | VERIFIED | 真机两次采样 |
| TOTAL 解析（user+kernel） | VERIFIED | 57.0% / 59.0% 实算 |
| 采样过期判定 | VERIFIED | 151s 样本被判 UNKNOWN |
| loadavg 独立解析 | VERIFIED | 真机 24.69/25.91/25.47 |
| top 数据源 | **UNVERIFIED** | 探测通道被策略拦截，应用内未验证 |
| CPU 卡片真机显示 | **UNVERIFIED** | 未在真机打开监控页截图确认 |
| 悬浮窗 CPU 显示 | **UNVERIFIED** | 未在真机启动悬浮窗 |
| Kotlin 编译 | VERIFIED | 本地 |
| 单元测试 | VERIFIED | CI |
| APK 构建 | VERIFIED | CI |

## G. 已知限制

1. **dumpsys 采样周期不固定**：实测同一会话内既有 6 秒新鲜样本，也有 151 秒过期样本。
   过期时 UI 会显示「—」并给出原因，不显示旧缓存。
2. **top 未验证**：当前 DSHA 通道禁止 `top`，无法确认 ColorOS 上 Toybox top 的输出格式；
   解析器已按保守规则实现（只认整机汇总行，不认进程行）。
3. **Load Average 不参与 CPU%**：`/proc/loadavg` 仅作为独立负载信息展示。
4. **`/proc/stat` 仍保留**：作为「内核采样」补充项，不与 dumpsys 结果混用。
5. **未修改 SELinux / 未使用 su / 未写入任何节点**：全部为只读诊断命令。

生成时间：2026-10-31
