# 瓦镜 Pro 2.7.1

## 电压实时刷新修复

- 根因：`BatteryManager.EXTRA_VOLTAGE` 来自粘性广播，部分 ROM 十几秒才更新一次，所以电压看起来不动。
- 修复：每秒优先只读标准 sysfs `voltage_now`，并校验整包/单芯量级，实时电压优先用于展示和功率计算。
- sysfs 不可读时自动回退广播电压，详情页会说明。
- 总压/单芯电压、自动电芯识别、功率算法保持不变。

## 版本

- versionCode 37 / versionName 2.7.1
- 328 项自动化检查通过
- APK SHA-256：`d0a0edda3b5f76cd320283b7e693d98c68713e7c85c90b3bb9e3c3847423b46b`
