# 瓦镜 Pro 2.2.3

## UI 调整

- 设备信息移到主页标题下方副标题：厂商 · 品牌 型号 · Android 版本。
- 不新增卡片，不打断主页视觉节奏；点击标题区域可打开完整详情。
- 详情页仍保留完整设备信息（厂商/品牌/型号/设备代号/Android/ROM/ABI）。

## 检查

- 排行汇总、主界面、排行页、详情、系统状态、通知和设置的空值/长文本复查。
- 安全复查：无 INTERNET、无 shell、无多余危险权限；应用排行使用可选的 PACKAGE_USAGE_STATS；导出组件仍只有启动页和受系统权限保护的 Shizuku Provider。
- 312 项自动化检查通过。

## 版本

- versionCode 25 / versionName 2.2.3
- APK SHA-256：`d69d8de64b4f0cffa7dbf11b50493feae2b70df9cc6c7d746c7259f1f733e036`
