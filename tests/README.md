# PowerMeter 核心算法测试（纯 JVM，无 JUnit）

不依赖 JUnit / Mockito / Android 运行时：编译用 `android.jar` 提供符号，运行时只用
`src/com/dsh/powermeter/core` 编译产物（`BatteryManager` 的常量已被 javac 内联）。

当前规模：**单元测试 286 条 + 静态扫描 15 条 = 301 条用例，0 失败**。

## 运行

```bash
bash tests/run_tests.sh
# 或指定平台 jar
ANDROID_JAR=/root/android/sdk/android-36/android.jar bash tests/run_tests.sh
# 或只跑安全静态扫描
bash tests/security_scan.sh
```

输出每条用例一行 `PASS` / `FAIL`，末尾打印 `==== 结果：通过 N / 失败 M ====`。
失败（含编译失败、进程崩溃、用例数少于下限）时退出码非 0，`build.sh` 会因此中断。

## 目录

```
tests/
├── run_tests.sh                # 编译 core+deps+tests → 跑测试 → 跑静态扫描 → 汇总
├── security_scan.sh / .py      # 安全与 PRD 静态扫描（清单 / 源码）
├── .gitignore                  # 忽略 build/ 等编译产物
└── com/dsh/powermeter/qa/
    ├── TestKit.java            # 断言框架（每条断言计 1 个用例，失败打印期望/实际）
    ├── Fixtures.java           # RawBattery 采样夹具（状态/plugged 常量与 BatteryManager 一致）
    ├── Runner.java             # 测试入口，逐组运行并汇总
    ├── SignNormalizationTests  # PRD Bug 1：vivo 负电流符号归一化
    ├── DualCellTests           # PRD Bug 2：双电芯 67W 修正 / 18W 不翻倍 / DUAL_OFF
    ├── UnitDetectionTests      # PRD Bug 3：µA / mA 单位识别（连续 4 次切换）
    ├── BoundsTests             # 电压 / 电流 / 温度越界 → 无效读数
    ├── PowerSmootherTests      # 插拔尖峰不跳变、首帧不爬升
    ├── PeakTrackerTests        # 3–5 秒平滑峰值，单点尖峰不算峰值
    ├── ChargeTimeEstimatorTests# 1 分钟 ~ 6 小时合理区间、低功率/满电 -1
    ├── SessionTrackerTests     # 会话开始/结束/摘要
    └── AbnormalDetectorTests   # 刚插电不误报、低功率满 30 秒才报、过温立即报
```

## 编译期依赖说明

`core` 的 `ChargeEngine` / `BatteryReader` 引用 App 层的 `Prefs` / `BrandCompat`，
因此 `run_tests.sh` 会把这两个文件一起交给 javac；运行时不会加载它们，
测试只实例化纯算法类。

## PRD 高风险用例一览

| 风险 | 用例（断言） |
| --- | --- |
| 充电中 `current_now` 为负显示负功率 | `SignNormalizationTests.chargingNegativeCurrent`（-2.4A → +2.4A / 10.32W） |
| 放电中为正被显示成充电 | `dischargingPositiveCurrent`（+1.2A → -1.2A / -5.04W） |
| 充电中电流≈0 显示错误数值 | `chargingNearZeroCurrent`（5mA → nearZero、chargeW=0、UNAVAILABLE） |
| 67W 双电芯只按单电芯算 | `DualCellTests.dualCell67W`（4.3V×7.7A → 8.6V、66.22W） |
| POWER_MAX=0 双电芯品牌漏修正 | `brandHighCurrentWithoutPowerMax`（6A@4.4V → 52.8W） |
| 18W 单电芯被翻倍 | `singleCell18WMustNotDouble`（4.2A@4.3V → 18.06W，cells=1） |
| DUAL_OFF 仍乘 2 | `dualOffDisablesCorrection`（→ 33.11W） |
| 整包电压被再乘 2 | `packVoltageAlreadyReported`（8.6V 已上报 → 25.8W） |
| mA ROM 显示 5mA | `UnitDetectionTests.milliDeviceByStreak`（连续 4 次 → 5A / 21.5W） |
| 开关机/涓流误切单位 | `milliSwitchRequiresFourConsecutive`、`plausibleMicroNeverSwitches` |
| 越界电压/电流算出错误功率 | `BoundsTests`（1.5V / 12V / 40A → invalid、chargeW=0） |
| 插拔瞬间展示值跳变 | `PowerSmootherTests.singleSpikeDoesNotJump`（45W↔0/200W 单点不跳） |
| 首帧从 0 爬升 | `firstFrameShowsFullValue`（首帧即 45W） |
| 峰值取到单点尖峰 | `PeakTrackerTests.pipelineRejectsSingleSpike`（200W 尖峰峰值仍 45W） |
| 充满预估荒谬 | `ChargeTimeEstimatorTests.reasonableRangeMatrix`（60s ~ 6h） |
| 异常检测插电误报 | `AbnormalDetectorTests.noFalseAlarmOnPlugIn`（29.999s 不报 / 30s 才报） |
| 清单可联网 / 组件导出 | `security_scan.py`：无 INTERNET、非 launcher 全 `exported=false` |
| 进程执行 / shell uid 信任 | `security_scan.py`：无 `Runtime.exec`、`ProcessBuilder`、uid 2000 信任 |
