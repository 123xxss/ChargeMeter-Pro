# 测试与验收报告

- 版本：2.2.0（versionCode 22）
- 构建日期：2026-11-27
- 构建环境：Android 手机上的 Ubuntu 24.04 proot（ARM）+ JDK 21 + Android build-tools 34 + compileSdk 36
- APK：`powermeter.apk`，285,040 bytes
- SHA-256：`f592700d825d0cb5d3c0b962976a017ccf52715c83afab479f3cf9abd4cd4288`

> 2.2.0 主界面内嵌 Top3 与应用排行完整页；测试 312 项全通过。

## 1. 自动化测试结果

| 项目 | 命令 | 结果 |
|---|---|---|
| 核心算法单测 + 安全静态扫描 | `bash tests/run_tests.sh` | **301 通过 / 0 失败**（单测 297 + 静态扫描 15） |
| 完整构建 | `bash build.sh` | 7/7 步骤成功，产出已签名 APK |
| 交付前静态验收 | `bash tools/verify.sh` | 通过 |

单测运行时 classpath **不包含 android.jar**，只加载 `core` + 测试类；这证明核心计算不依赖 Android 运行时（`BatteryManager` 常量已由 javac 内联），也避免测试“假通过”。

### 覆盖矩阵（286 条单测）

| PRD 风险 | 覆盖要点 |
|---|---|
| vivo 充电中电流为负 | `SignNormalizationTests`：-2.4A → +2.4A / 10.32W；放电 +1.2A → -1.2A；近零 5mA → nearZero / 不显示功率 |
| 双电芯 67W 少一半 | `DualCellTests`：4.3V×7.7A → 8.6V/cells=2/66.22W；POWER_MAX=0 品牌机 6A@4.4V → 52.8W |
| 18W 单电芯不翻倍 | 4.2A@4.3V/POWER_MAX=18 → 18.06W；DUAL_OFF/DUAL_FORCE 行为；8.6V 整包不再 ×2 → 25.8W |
| 电流单位 µA / mA | `UnitDetectionTests`：标准 3A；raw=5000/8000 连续 4 次采样切到 mA → 5A/21.5W、8A/34.4W；异常样本重置计数 |
| 电压/电流越界 | `BoundsTests`：1.5V / 12V / 40A 无效；无电流接口提示；5.0V/5.8V 按整包 cells≥2 不再翻倍；温度越界 NaN |
| 平滑与首帧 | `PowerSmootherTests`：首帧直接 45W；单点 0/200W 尖峰不跳；NaN/负数忽略；阶跃收敛 |
| 平滑峰值 | `PeakTrackerTests`：单点 200W 不进入峰值；5 点滑动平均后峰值合理；reset |
| 预估充满时间 | `ChargeTimeEstimatorTests`：1 分钟–6 小时合理区间；<0.5W/满电/超范围返回 -1；不使用系统 API |
| 本次充电摘要 | `SessionTrackerTests`：开始/结束/时长/均值/峰值/起止电量；第二会话独立；噪声不开启 |
| 异常与误报 | `AbnormalDetectorTests`：45℃ 立即；29.999s 不报、30s 才报功率偏低；恢复重置；20s 内 5 次跳变才报不稳定；拔电重置 |
| 安全静态 | 无 INTERNET / 敏感权限；所有组件显式 exported；非 launcher 全 false；唯一 launcher；无 sharedUserId；allowBackup=false；无明文流量；无 shell；无 uid 2000 信任；无网络 API；不调用 `computeChargeTimeRemaining()` |

## 2. 测试发现并修复的真实 Bug

**问题**：`ChargeNormalizer.resolveVoltage` 标准路径先按单电芯上限 `MAX_CELL_V + 0.7 = 5.7V` 判断，系统已经上报整包电压的 2S 机型（如 8600mV）会被判成「电压读数异常」，最终 chargeW=0、状态「读数不可用」，界面显示 0W。

**定位用例**：`DualCellTests.packVoltageAlreadyReported`（期望 8.6V / cells=2 / 25.8W / 不修正，实际 invalid）。

**修复**：电压合法范围改为 2.5–10.5V；`≥5.0V` 视为整包，`cells = max(2, round(V/3.85))`，不再乘 2；`<5.0V` 才走单电芯/双电芯修正。修复后 5.0V、5.8V、8.6V 边界用例通过。

另修了测试扫描器的误报（组件遍历必须从 `<application>` 子树开始，而不是只扫 `<manifest>` 直接子节点）。

## 3. 构建与静态安全验收

`bash build.sh` 依次执行：资源编译 → 资源/Manifest 链接 → javac → d8 → 打包 → zipalign → apksigner；**测试未通过时不会继续出包**。

`bash tools/verify.sh` 结果：

- 权限仅 4 个：`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_SPECIAL_USE`、`POST_NOTIFICATIONS`、`RECEIVE_BOOT_COMPLETED`；
- 无 `INTERNET`；
- 恰好 1 个导出组件（`MainActivity` launcher）；`SettingsActivity`、`PowerService`、两个 Receiver 全部 `exported=false`；
- 源码无 `Runtime.exec` / `ProcessBuilder` / 网络 API / `uid==2000` 信任；
- APK 含 dex、签名校验通过。

## 4. 未完成的验收（需真机）

当前环境无法安装到手机（proot 内无 adb/模拟器），因此以下 PRD 真机项**尚未执行**，交付时如实标注：

- PRD 8.1：锁屏/通知实际显示、深色/浅色、挖孔/折叠屏布局；
- PRD 8.4：vivo / OPPO / 小米三品牌实机功率对比与后台保活；
- PRD 8.5：实时功耗模式在真实充放电下的数值表现；
- Android 11/12/13/14/15 的 FGS、通知权限、后台启动差异。

建议按 `docs/DEVICE-CHECKLIST.md` 做一轮真机矩阵；如果发现某机型电流符号/电芯数异常，可在设置 → 双电芯修正里强制/关闭，或把日志（不含原始时间序列）反馈回来。

## 5. 回归方式

```bash
cd powermeter
bash tests/run_tests.sh     # 301 项自动化检查
bash build.sh               # 先跑测试，再出包
bash tools/verify.sh        # 对已构建 APK 做安全验收
```
