# 充电功率 Pro · 项目报告

> 版本：1.9.1（versionCode 19）  
> 包名：`com.dsh.powermeter`  
> 平台：Android 11–16（minSdk 29 / targetSdk 36）  
> 参考项目：[ChargeMeter](https://github.com/123xxss/ChargeMeter)  
> 当前状态：已在 Android 16 真机确认功率显示正常；312 项自动化检查通过。  
> 作者：[123xxss](https://github.com/123xxss)  
> 仓库：[123xxss/ChargeMeter-Pro](https://github.com/123xxss/ChargeMeter-Pro)  
> 下载：[ChargeMeter-Pro-v1.9.1.apk](https://github.com/123xxss/ChargeMeter-Pro/releases/download/v1.9.1/ChargeMeter-Pro-v1.9.1.apk)

---

## 1. 这个 App 能干什么

**一句话：把“充电器标称多少 W”变成“此刻真正进入电池多少 W”，并在锁屏/通知栏/主界面用不打扰的方式实时显示。**

适合这些问题：

- 想知道充电器、数据线、充电协议是否真的满血；
- 充电变慢、发热、接触不良时，想快速判断是不是掉功率；
- 想观察边充边用的净功耗/放电功耗；
- 不想用带广告、联网、上传数据的电池工具。

### 核心能力

| 能力 | 说明 |
|---|---|
| 实时入电池功率 | `P = |I| × V × 电芯数 × 校准倍率`，1 秒刷新，两位小数 |
| 充电档位 | 普通（<5W）/ 快速（5–30W）/ 超级快充（≥30W），异常降速优先提示 |
| 状态机 | 未充电、充电中、已暂停、已充满、异常、读数不可用、净功耗接近零 |
| 主界面功率卡 | 自绘 236dp 紧凑卡，功率 + 状态 + 档位 + 电量 + 温度 + 预估 + 详情入口 |
| 实时功率曲线 | 最近 2 分钟、1 秒采样、120 点 |
| 通知栏 / 锁屏卡片 | 常驻低优先级通知，充电时显示 `14.85 W · 快速充电`、电量/温度/预计充满 |
| 充电详情 | 电压、电流、温度、电芯数、平滑峰值、剩余时间、数据来源、口径说明 |
| 实时功耗检测 | 可选子开关：充电看净功耗，放电显示“放电功耗”，近零显示“接近零” |
| 电芯数 | vivo/OPPO/小米/荣耀默认 2 芯，其余 1；可手动 1–4 |
| 功率校准 | 输入系统或功率计显示的功率，反推本机倍率 |
| 本次充电摘要 | 时长、平均功率、峰值、起止电量，仅本地保存 |
| 配件提示 | 持续低功率或剧烈波动时给中性提示，不误报插拔瞬间 |
| 后台白名单引导 | vivo/OPPO/小米按品牌给设置路径并尝试深链 |
| Shizuku 只读增强 | 可选、默认关、实验性；标准接口不可用时只读兜底，不 shell、不写文件 |
| 崩溃自检 | 本地记录上次异常，下次启动展示并一键复制；UI 异常进入安全模式 |

### 明确不做

- 不做长期充电历史、云端同步、账号体系；
- 不做应用级/子系统级功耗拆分；
- 不读取充电器协议或 VBUS（普通应用拿不到）；
- 不申请 INTERNET、定位、存储、通讯录、电话状态等无关权限；
- 不通过 Shizuku 执行 shell、写 settings、改包管理；
- AOD 没有系统资质，不做“可见但无效”的入口。

---

## 2. 功率算法（与 ChargeMeter 对齐）

### 2.1 公式

```text
电流 A   = CURRENT_NOW / 1,000,000        // 标准 µA
电压 V   = EXTRA_VOLTAGE / 1,000          // 标准 mV
整包电压 = 电压 V × 电芯数
功率 W   = |电流 A| × 整包电压 × 校准倍率
```

**单位兜底**：若计算出的功率 > 400W，则把电流按 mA 再解释一次（参考 ChargeMeter 的做法）。

**有效条件**：
- `CURRENT_NOW` 可用；
- `EXTRA_VOLTAGE` 在 **2500–5000mV**（参考项目口径）；
- 充电中电流接近 0 时显示“接近零/不可用”，不输出误导性 0.0W。

### 2.2 电芯数

参考 ChargeMeter：**不做自动猜**，避免误判。

- vivo / OPPO / 小米 / 荣耀：默认 **2 芯**
- 其它品牌：默认 **1 芯**
- 设置 → 数据口径 → **电芯数**：自动 / 1 / 2 / 3 / 4
- 之前版本有“双电芯修正”第二套逻辑，1.5.1 已移除，避免两套配置互相覆盖；
  只保留“电芯数”这一处口径。

### 2.3 校准

设置 → 数据口径 → **功率校准**：输入系统/外部功率计显示的当前功率，App 反推倍率并持久化。

校准倍率与电芯数都会参与最终功率；曲线、峰值、摘要、通知都使用同一算法。

### 2.4 实时刷新

- 充电中固定 **1 秒刷新一次**；
- 主界面、通知、详情、曲线使用同一帧数据；
- 不额外平滑，避免“显示值追不上真实变化”；
- 功率曲线的 120 个采样点按 1 秒采样，覆盖最近 2 分钟。

---

## 3. 更新了什么

### 1.0.0 — 基础实现

- 按 PRD V1.4 实现：电池广播读取、功率计算、状态机、锁屏通知、主界面/设置/详情；
- 无网络权限、无广告。

### 1.1.0 — Android 16 启动加固 + Pro 品牌

- 应用更名「充电功率 Pro」，新图标；
- 启动从 `startForegroundService` 改为可见界面下的 `startService`，避免 OEM 拒绝前台服务导致系统级崩溃；
- 前台通知先最小字段进入前台，再换完整卡片；
- 安全模式、本地崩溃报告。

### 1.1.1 — 修复启动 NPE

- 修复 Android 16 上：
  `NullPointerException: PhoneWindow.getInsetsController() ... DecorView null`
- 根因：`setContentView` 之前调用了 `applySystemBars()`；
- 修复：先构建内容，再设置状态栏/导航栏，并对 `DecorView` 判空。

### 1.1.2 / 1.2.0 — 卡片与硬件路径

- 卡片曾出现“只有状态标签、一大片空白、高度撑满”；
- 改为更高的紧凑固定高度，最终在 1.2.0 变成**自绘 236dp 功率卡**（不再依赖嵌套 LinearLayout 测量）；
- 新增充电动画：进度条流光 + 呼吸闪电；
- 新增 Shizuku 只读增强读取（实验性）。

### 1.3.0 / 1.3.1 — 电芯数与刷新

- 双电芯自动匹配增加已知机型、`voltage_max`、POWER_MAX/高电流三层判定；
- 刷新从 1 秒提升到 0.5 秒（1.3.1），后又按用户要求回到 1 秒（1.4.1）。

### 1.4.0 — 参照 ChargeMeter

- 功率口径改为 ChargeMeter 公式；
- vivo/OPPO/小米默认 2 电芯；
- 两位小数、2 分钟功率曲线、功率校准。

### 1.5.0 — 完全采用 ChargeMeter 功率算法

- 主功率完全按参考项目公式；
- 电芯数品牌默认 + 手动；
- 电压 2500–5000mV 校验、>400W 按 mA 兜底；
- 去掉额外平滑，1 秒直接显示。

### 1.5.1 — 清理重复设置 + Shizuku 兜底

### 1.6.0 — UI 与细节优化

### 1.7.0 — 系统状态 + Apple UI + 实时详情

### 1.7.1 — 详情滚动 + Shizuku 系统读取

### 1.7.2 — CPU 兜底

### 1.8.0 — 五风格 + 全 App 动画 + 设备信息

### 1.9.0 — 主页 + 详情页 UI 重做

### 1.9.1 — 详情圆角底板 + 动效选项 + 通知同步（当前版本）

- 详情页实心圆角底板、删除“详情”标题、防止文字溢出；
- 标准/增强/关闭三档动效；
- 通知栏每帧与主界面同步。

- 主页状态胶囊、实时数据 2×2 大卡、统一层级；
- 详情页实时功率大卡 + 充电/系统/设备分组卡片；
- 分区淡入、数值淡入、弹窗/二级页动画。

- 新增薄荷、紫色星云风格，共 5 种；
- 页面/主界面/设置/详情弹窗动画统一；
- 详情与关于显示设备/系统/ROM/ABI；
- Shizuku 只读系统读取覆盖 CPU/GPU/温度，修复弹窗视口。

- CPU 统计直读失败时通过 Shizuku UserService 兜底；
- 详情滚动修复保留。

- 详情弹窗固定视口 + ScrollView，关闭按钮/说明可完整滚动；
- GPU/系统温度未开放时提供 Shizuku 授权与读取入口；
- UserService 新增只读 readSystem。

- 系统状态卡：CPU/内存/GPU/温度/存储；
- 详情弹窗实时订阅刷新；
- Apple 极简（默认）/经典渐变/深色霓虹三种风格；
- 长按复制功率、曲线当前值/新会话清空。

- 曲线铺满、时间/当前值标注、新会话清空；
- 充电动画不可见时停止；长按卡片复制功率；通知两位小数；
- 清理旧卡片字段与废弃双电芯设置代码。

- 移除“双电芯修正”重复入口，只保留“电芯数”；
- Shizuku/增强读取在标准 BatteryManager 电压/电流不可用时，作为只读兜底参与参考算法；
- 新增参考算法回归测试（用户截图数据 3.95V / 1.88A / 2 芯 = 14.85W）；
- 测试总数 312 项。

---

## 4. 修复了什么 Bug

| Bug | 影响 | 修复 |
|---|---|---|
| Android 16 启动 NPE | 打开即闪退 | 先 `setContentView` 再设置系统栏，`DecorView` 判空/兜底 |
| 卡片被拉满屏幕 | 只剩状态标签，无法看功率 | 自绘 236dp 卡片，固定测量，内容全部 Canvas 绘制 |
| 卡片子视图不显示 | 主功率缺失 | 不再依赖嵌套 LinearLayout/weight，全部自绘 |
| 低功率双电芯不 ×2 | 67W 双电芯只显示一半/更低 | 对齐 ChargeMeter：品牌默认 2 芯，可手动 1–4 |
| 双电芯设置与电芯数冲突 | 两套配置互相覆盖 | 1.5.1 移除“双电芯修正”，只留电芯数 |
| 18W 单电芯被品牌默认翻倍 | 单电芯机型可能显示 2 倍 | 设置 → 电芯数 → 手动 1 芯；这是参考项目口径的已说明边界 |
| Shizuku 开关没有实际作用 | 开了也不影响功率 | 标准接口不可用时作为只读兜底参与算法，并显示来源 |
| 刷新显示滞后 | 看起来不像实时 | 固定 1 秒采样，去掉额外平滑；主界面/通知/曲线共用同一帧 |
| 详情与主界面口径不一致 | 用户困惑 | 全部使用同一 `PowerSnapshot` |
| 通知权限被拒绝时风险 | 可能崩溃/误导 | 捕获权限异常，主界面横幅引导，不阻塞锁屏主功能 |
| Android 12+ 后台启动前台服务失败 | 可能进程级崩溃 | 可见界面用 `startService`，最小前台通知，异常兜底 |

---

## 5. 功能细节

### 主界面

- 顶部：应用名 + 设置入口；
- 自绘功率卡：状态胶囊、档位、主功率（两位小数）、模式说明、电量条、温度/预计充满、数据来源、详情按钮；
- 四宫格：电压（整包）、电流、温度、平滑峰值；
- 功率曲线：最近 2 分钟；
- 本次/上次充电摘要；
- 底部口径说明：进入电池功率，不等同充电器输入功率。

### 通知栏 / 锁屏

- 低优先级、无声、无振动、锁屏可见；
- 充电中：`14.85 W · 快速充电` + 电量/温度/预计充满 + 电量进度；
- 未充电：只显示“监测中/接入电源后自动显示”，不显示功率数字；
- 已充满/暂停/异常/不可用/净功耗近零都有独立文案；
- 通知权限撤销时静默降级，不崩溃。

### 设置页

- 基本：总开关；
- 显示与通知：状态栏通知、实时功耗、温度/预估/档位开关；
- 数据口径：**电芯数**、功率校准、增强读取、Shizuku 状态/开关；
- 后台运行：品牌白名单、通知权限；
- 关于：版本、无网络、口径说明。

### Shizuku（实验性）

- 设置中可查看“未安装/未授权/已授权”；
- 通过官方 Shizuku API + 只读 UserService 读取标准 sysfs 电池节点；
- 只读、无 shell、无写文件；未授权/断连/服务被杀自动回退；
- 标准 BatteryManager 可用时不依赖 Shizuku。

---

## 6. 架构

```text
app/src/com/dsh/powermeter/
  core/
    BatteryReader          电池广播 + CURRENT_NOW + 可选 sysfs/Shizuku
    ChargeNormalizer       参考算法 / 旧归一化路径（测试用）
    ChargeEngine           1 秒采样、状态、峰值、曲线/摘要数据
    PowerSnapshot          一帧展示快照
    StateStore             进程内订阅
    ..., Smoother/Peak/Session/Estimator/Abnormal
  service/
    PowerService           前台服务
    NotificationHelper     通知/锁屏卡片
    PowerConnectionReceiver / BootReceiver
  ui/
    PowerCardView          自绘功率卡 + 充电动画
    PowerChartView         2 分钟功率曲线
    UiKit / DetailDialog
  shizuku/
    BatteryUserService     只读 UserService
    ShizukuBatterySource   授权/绑定/读取/回退
  MainActivity / SettingsActivity
  Prefs / BrandCompat / CrashReporter
```

数据流：

```text
BatteryManager / sysfs / Shizuku
        ↓ 每秒
RawBattery → ChargeNormalizer（参考算法）
        ↓
PowerSnapshot → StateStore
        ↓
主界面自绘卡 / 曲线 / 通知栏 / 详情
```

---

## 7. 权限与隐私

仅申请：

- `FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_SPECIAL_USE`：常驻监测；
- `POST_NOTIFICATIONS`：状态栏/锁屏卡片；
- `RECEIVE_BOOT_COMPLETED`：开机恢复；
- `moe.shizuku.manager.permission.API_V23`：Shizuku 授权（可选功能，默认关）。

**没有** `INTERNET`、定位、存储、通讯录、电话状态、`BATTERY_STATS`、悬浮窗权限。

- 所有数据只在本机处理，不上传、不同步、不联网；
- 日志不打印原始电压/电流完整序列；
- 崩溃报告只存应用私有目录，下次启动可复制，不自动外发；
- 主界面/设置/详情均不导出；Shizuku Provider 按官方要求导出，并由 `INTERACT_ACROSS_USERS_FULL` 保护。

---

## 8. 构建与测试

### 构建

```bash
cd powermeter
bash build.sh
# 输出：powermeter.apk
```

依赖：JDK 17+、Android build-tools、`android.jar`。支持 x86_64 主机与 ARM 手机 proot（自动用 `qemu-x86_64-static` 跑 aapt2/zipalign）。

### 测试

```bash
bash tests/run_tests.sh
```

当前结果：**312 项通过**（297 算法单测 + 15 安全/PRD 静态扫描）。

测试覆盖：

- 参考算法：3.95V/1.88A/2 芯 = 14.85W；
- 单电芯 18W、67W 双电芯；
- 电流单位 µA/mA、符号、越界、温度；
- 平滑/峰值/预估/会话/异常；
- 无 INTERNET、导出组件、无 shell、无 uid2000 信任、不调用系统 computeChargeTimeRemaining。

APK（1.5.1）：219,155 bytes，SHA-256：

```text
13ede6292d5fe3b18de3305be5646067dbc1a9badcb67ac843a75aac5f12cf5e
```

---

## 9. 已知限制 / 使用注意

- 参考算法要求系统上报**单电芯电压**（2500–5000mV）；如果机型直接上报整包电压，会显示“不可用”。这是对齐 ChargeMeter 的结果，不是所有机型都能直接用。
- vivo/OPPO/小米/荣耀默认 2 芯，个别单电芯机型需要手动改成 1 芯。
- 显示的是**进入电池的功率**，不是充电器输入功率；边充边用/温控/线材都会让它低于标称。
- 锁屏卡片是通知形态，不是系统原生充电动画位置；AOD 无系统资质，不提供入口。
- Shizuku 增强是实验性能力，且需要用户安装/启动/授权 Shizuku；标准 BatteryManager 路径是主路径。
- 后台保活受 OEM 策略影响，需按 vivo/OPPO/小米引导设置白名单。

---

## 10. 后续计划

- 更多机型电芯数/电压口径验证；
- Shizuku 节点白名单逐机型评审；
- 可选的更细功率曲线与导出（本地）；
- 主题/显示位置自定义（P2）；
- 系统签名/厂商合作路径下的 AOD 与状态栏原生嵌入（P2）。

---

## 11. 文件结构

```text
powermeter/
├── AndroidManifest.xml
├── build.sh
├── README.md
├── CHANGELOG.md
├── LICENSE
├── docs/
│   ├── PROJECT-REPORT.md      ← 本文
│   ├── PRD-COMPLIANCE.md
│   ├── TEST-REPORT.md
│   ├── DEVICE-CHECKLIST.md
│   └── icon-512.png
├── res/
├── src/
├── aidl/
├── libs/                      Shizuku API / provider / shared / aidl
├── tests/
└── tools/
```

---

## 12. 反馈问题时请提供

1. 机型、ROM 版本、Android 版本；
2. 充电器/线材；
3. 主界面详情截图（电压/电流/电芯数/来源）；
4. 系统/功率计显示的参考功率；
5. 是否启用 Shizuku、后台白名单。

功率对比时请注明“入电池功率”，不要把充电器标称输入功率直接当基准。
