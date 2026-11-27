# ChargeMeter Pro 1.7.1

## 修复

- 充电详情弹窗高度超过屏幕、内容顶部/底部被裁切。
- 弹窗内部没有正确滚动视口，关闭按钮与说明文字无法完整看到。
- 现在详情弹窗固定为屏幕 78% 高度，内容全部由 ScrollView 滚动。

## 新增

- GPU/系统温度显示“系统未开放”时，主界面和详情页提供「用 Shizuku 尝试读取」入口：
  - 未检测到 Shizuku → 提示先安装/启动；
  - 未授权 → 请求授权；
  - 已授权 → 开启只读增强并重新读取。
- Shizuku UserService 新增只读 `readSystem()`，尝试读取 thermal/GPU sysfs；仍不可读则保持 `--`。

## 版本

- versionCode 15 / versionName 1.7.1
- 312 项自动化检查通过
- APK SHA-256：`610e4e95cd7143e310f2889a95cf6188ac1f1486866d21ce17d19a57a89c2839`
