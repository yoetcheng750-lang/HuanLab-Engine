# YOET V5.2.2 · CPU Single Source Cleanup

## IDENTITY LOCK
com.huanlab.engine

## VERSION
versionCode = 4
versionName = 1.2.2

## CPU SOURCE
dumpsys cpuinfo ONLY

## FALLBACK
top = REMOVED（CpuTopParser.kt 已删除，APK dex 中不存在）
loadavg = REMOVED（CpuLoadAvgParser.kt 已删除，APK dex 中不存在）

## CPU UI
首页顶部卡片 = 使用 state.cpuUsage（PASS：代码层面）
详情卡片 = 仅百分比 + 数据来源（PASS：代码层面）
监控页面 = 使用 state.cpuUsage（PASS：代码层面）
悬浮窗 = 使用 state.cpuUsage（PASS：代码层面）

## DATA VALIDATION
合法 TOTAL 行 = PASS（真机两轮采样均唯一命中）
过期数据拒绝 = PASS（151s 样本判 UNKNOWN）
读取失败显示 — = PASS（valuePercent=null 路径）
旧缓存清除 = PASS（SamplingCoordinator 失败时写入 UNKNOWN，不保留旧值）

## TEST
单元测试 = PASS（CI：47→ 调整后全部通过）
Kotlin 编译 = PASS（本地 BUILD SUCCESSFUL）
APK 构建 = PASS（CI success）

## CORE LOCK
25/25 = PASS

## GITHUB ACTIONS
Run ID = 41192843925
结果 = PASS

## APK
文件名 = YOET-v5.2.2-debug.apk
大小 = 11,128,964 字节
SHA-256 = ca3ac420290049d5b39ae8e017e24346cf9c40d9c179fa51e8d354afcf13a144

## DEVICE VERIFICATION
已验证项目 = applicationId、版本 3→4、核心 25 文件、编译、单元测试、APK 构建、dex 中降级类已消失
UNVERIFIED 项目 = 首页/详情/监控/悬浮窗在真机上的实际显示、失败态「—」的真机表现、旧值清除的 UI 观察

## FINAL STATUS
PASS（构建与代码层面）；真机 UI 显示项标注 UNVERIFIED
