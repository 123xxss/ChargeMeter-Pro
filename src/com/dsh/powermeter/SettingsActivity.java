package com.dsh.powermeter;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.dsh.powermeter.core.ChargeEngine;
import com.dsh.powermeter.core.StateStore;
import com.dsh.powermeter.service.PowerService;
import com.dsh.powermeter.shizuku.ShizukuBatterySource;
import com.dsh.powermeter.ui.UiKit;

/**
 * 设置页：总开关、显示项、数据口径、后台运行与关于。
 *
 * 全部界面程序化构建，不新增布局资源；不包含网络、文件或危险权限相关操作。
 */
public final class SettingsActivity extends Activity {

    private LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 同 MainActivity：先创建 DecorView / setContentView，再设置系统栏外观。
        buildShell();
        applySystemBars();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applySystemBars();
        ShizukuBatterySource.setPermissionListener(this::runOnUiThread2);
        rebuildContent();
    }

    private void runOnUiThread2() {
        runOnUiThread(this::rebuildContent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ShizukuBatterySource.setPermissionListener(null);
    }

    // ---------------------------------------------------------------- 操作

    private void onMasterChanged(boolean enabled) {
        Prefs.get(this).setMasterEnabled(enabled);
        try {
            if (enabled) {
                PowerService.startFromActivity(this);
                ChargeEngine.get(this).sampleNow();
            } else {
                PowerService.stop(this);
            }
        } catch (Throwable ignored) {
        }
    }

    private void showDualDialog() {
        final Prefs prefs = Prefs.get(this);
        final String[] items = {"自动（推荐）", "强制 ×2 修正", "关闭修正"};
        AlertDialog.Builder builder = dialogBuilder("双电芯修正");
        builder.setSingleChoiceItems(items, prefs.dualCellMode(), (dialog, which) -> {
            Prefs.get(this).setDualCellMode(which);
            ChargeEngine.get(this).sampleNow();
            dialog.dismiss();
            rebuildContent();
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void showCalibrationDialog() {
        EditText input = new EditText(this);
        input.setHint("输入当前功率 W，例如 22.91");
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        com.dsh.powermeter.core.PowerSnapshot latest = StateStore.get().latest();
        double current = latest == null ? 0 : latest.powerW;
        AlertDialog.Builder builder = dialogBuilder("功率校准");
        builder.setMessage("输入另一台设备或系统显示的当前功率，App 会反推出倍率。\n当前显示："
                + (current > 0.05 ? String.format(java.util.Locale.ROOT, "%.2f W", current) : "暂无"));
        builder.setView(input);
        builder.setPositiveButton("校准", (dialog, which) -> {
            try {
                double target = Double.parseDouble(input.getText().toString().trim());
                if (target <= 0 || current <= 0.05) {
                    Toast.makeText(this, "当前功率过小，暂时无法校准", Toast.LENGTH_SHORT).show();
                    return;
                }
                Prefs.get(this).setPowerRatio(target / current);
                ChargeEngine.get(this).sampleNow();
                Toast.makeText(this, "校准完成", Toast.LENGTH_SHORT).show();
                rebuildContent();
            } catch (Throwable t) {
                Toast.makeText(this, "请输入有效数字", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void showGuideDialog() {
        AlertDialog.Builder builder = dialogBuilder("后台白名单引导");
        builder.setMessage(BrandCompat.guideSteps());
        builder.setPositiveButton("去设置", (dialog, which) -> {
            Prefs.get(this).markGuideShown();
            BrandCompat.openBackgroundSettings(this);
        });
        builder.setNegativeButton("关闭", null);
        builder.show();
    }

    private AlertDialog.Builder dialogBuilder(CharSequence title) {
        int theme = isNight()
                ? android.R.style.Theme_DeviceDefault_Dialog_Alert
                : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert;
        AlertDialog.Builder builder = new AlertDialog.Builder(this, theme);
        builder.setTitle(title);
        return builder;
    }

    private boolean notificationsAllowed() {
        try {
            NotificationManager nm = (NotificationManager)
                    getSystemService(Context.NOTIFICATION_SERVICE);
            return nm == null || nm.areNotificationsEnabled();
        } catch (Throwable t) {
            return true;
        }
    }

    private boolean isNight() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private void applySystemBars() {
        UiKit.styleSystemBars(this, getColor(R.color.bg), !isNight());
    }

    // ---------------------------------------------------------------- 界面搭建

    private void buildShell() {
        LinearLayout root = UiKit.column(this);
        root.setBackgroundColor(getColor(R.color.bg));

        LinearLayout header = UiKit.row(this);
        header.setPadding(UiKit.dp(this, 8), UiKit.dp(this, 6),
                UiKit.dp(this, 16), UiKit.dp(this, 6));
        header.addView(UiKit.backButton(this, v -> finish()));
        TextView title = UiKit.text(this, "设置", 20f, R.color.text_primary, true);
        header.addView(title, UiKit.left(this, UiKit.weighted(1f), 2));
        root.addView(header, UiKit.matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(getColor(R.color.bg));

        content = UiKit.column(this);
        content.setPadding(UiKit.dp(this, 20), UiKit.dp(this, 2),
                UiKit.dp(this, 20), UiKit.dp(this, 28));
        scroll.addView(content, new FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        UiKit.applyEdgeToEdgeInsets(this, root);
    }

    private void rebuildContent() {
        content.removeAllViews();
        Prefs prefs = Prefs.get(this);
        boolean master = prefs.masterEnabled();
        boolean notificationsAllowed = notificationsAllowed();
        boolean ignored = BrandCompat.batteryOptimizationIgnored(this);

        // 基本
        content.addView(UiKit.section(this, "基本"), UiKit.matchWrap());
        addCard(new UiKit.SwitchRow(this, "实时充电功率显示",
                "总开关。关闭后停止采样与状态栏通知。", master, true,
                (button, checked) -> onMasterChanged(checked)));

        // 显示与通知
        content.addView(UiKit.section(this, "显示与通知"), UiKit.matchWrap());
        String notifSubtitle = notificationsAllowed
                ? "在状态栏常驻显示充电功率与电量"
                : "系统未允许通知，开启后才能显示状态栏卡片";
        addCard(new UiKit.SwitchRow(this, "状态栏通知", notifSubtitle,
                prefs.notificationEnabled(), true,
                (button, checked) -> Prefs.get(this).setNotificationEnabled(checked)));
        if (!notificationsAllowed) {
            addCard(UiKit.actionRow(this, "通知权限",
                    "系统未允许本应用发送通知", "去开启",
                    v -> BrandCompat.openNotificationSettings(this)));
        }
        addCard(new UiKit.SwitchRow(this, "实时功耗检测",
                "以净功耗 / 电池放电功耗作为主数值，未充电时也会刷新",
                prefs.realtimePowerEnabled(), true,
                (button, checked) -> Prefs.get(this).setRealtimePowerEnabled(checked)));
        addCard(new UiKit.SwitchRow(this, "显示温度",
                "在主界面与状态栏显示电池温度", prefs.showTemperature(), true,
                (button, checked) -> Prefs.get(this).setShowTemperature(checked)));
        addCard(new UiKit.SwitchRow(this, "显示预估充满时间",
                "根据当前功率与电量估算还需多久充满", prefs.showEstimate(), true,
                (button, checked) -> Prefs.get(this).setShowEstimate(checked)));
        addCard(new UiKit.SwitchRow(this, "显示充电档位",
                "显示普通 / 快速 / 超级快充标签", prefs.showTier(), true,
                (button, checked) -> Prefs.get(this).setShowTier(checked)));

        // 数据口径
        content.addView(UiKit.section(this, "数据口径"), UiKit.matchWrap());
        addCard(UiKit.actionRow(this, "双电芯修正",
                "部分双电芯机型只上报单电芯电压，需要 ×2 修正才接近实际功率",
                dualLabel(prefs.dualCellMode()), v -> showDualDialog()));
        addCard(UiKit.actionRow(this, "功率校准",
                "参照系统/功率计读数校准倍率（参考 ChargeMeter 的校准方式）",
                String.format(java.util.Locale.ROOT, "倍率 %.2f", prefs.powerRatio()),
                v -> showCalibrationDialog()));
        addCard(new UiKit.SwitchRow(this, "增强读取（实验性）",
                "默认关闭。只通过系统标准 sysfs 只读读取电池节点，部分机型不可用，不申请额外权限。",
                prefs.enhancedReadEnabled(), true,
                (button, checked) -> {
                    Prefs.get(this).setEnhancedReadEnabled(checked);
                    ChargeEngine.get(this).sampleNow();
                }));
        addCard(UiKit.actionRow(this, "Shizuku 状态",
                "只读增强读取：需要安装 Shizuku 并授权；未授权/断连时自动回退标准路径，不执行 shell、不写文件。",
                shizukuStatus(), v -> requestShizuku()));
        addCard(new UiKit.SwitchRow(this, "Shizuku 增强读取（实验性）",
                "默认关闭。通过 Shizuku UserService 只读标准电池节点，提升数据置信度；失败自动回退。",
                prefs.shizukuReadEnabled(), true,
                (button, checked) -> setShizukuEnabled(checked)));

        // 后台运行
        content.addView(UiKit.section(this, "后台运行"), UiKit.matchWrap());
        addCard(UiKit.actionRow(this, "后台白名单",
                "允许后台运行，避免锁屏或切后台后功率停止刷新",
                ignored ? "已允许" : "未设置",
                v -> BrandCompat.openBackgroundSettings(this)));
        addCard(UiKit.actionRow(this, "通知权限",
                "Android 13 起需要通知权限才能显示状态栏卡片",
                notificationsAllowed ? "已允许" : "未允许",
                v -> BrandCompat.openNotificationSettings(this)));

        LinearLayout oem = UiKit.card(this);
        oem.addView(UiKit.text(this, "机型提示 · " + BrandCompat.brandDisplayName(),
                15f, R.color.text_primary, true), UiKit.matchWrap());
        TextView steps = UiKit.text(this, BrandCompat.guideSteps(),
                12f, R.color.text_secondary, false);
        steps.setLineSpacing(UiKit.dp(this, 2), 1f);
        oem.addView(steps, UiKit.top(this, UiKit.matchWrap(), 6));
        if (BrandCompat.isTargetBrand()) {
            TextView again = UiKit.ghostButton(this, "重新查看后台白名单引导",
                    v -> showGuideDialog());
            oem.addView(again, UiKit.top(this, UiKit.matchWrap(), 12));
        }
        addCard(oem);

        // 关于
        content.addView(UiKit.section(this, "关于"), UiKit.matchWrap());
        LinearLayout about = UiKit.card(this);
        addInfo(about, "版本", "1.4.1");
        addInfo(about, "数据", "只在本机处理，无网络权限，不上传任何信息。");
        addInfo(about, "口径", "功率为进入电池的功率估算值；边充边用时可能低于充电器实际输出功率。");
        addInfo(about, "权限", "不申请定位、存储、悬浮窗、无障碍等权限。");
        addCard(about);
    }

    private void addCard(View view) {
        content.addView(view, UiKit.top(this, UiKit.matchWrap(), 8));
    }

    private void addInfo(LinearLayout parent, String label, String value) {
        parent.addView(UiKit.text(this, label, 13f, R.color.text_secondary, true),
                UiKit.top(this, UiKit.matchWrap(), 10));
        TextView v = UiKit.text(this, value, 13f, R.color.text_primary, false);
        v.setLineSpacing(UiKit.dp(this, 2), 1f);
        parent.addView(v, UiKit.top(this, UiKit.matchWrap(), 3));
    }

    private String shizukuStatus() {
        try {
            if (!ShizukuBatterySource.isAvailable()) return "未安装/未运行";
            if (!ShizukuBatterySource.hasPermission()) return "未授权";
            return "已授权";
        } catch (Throwable t) {
            return "不可用";
        }
    }

    private void requestShizuku() {
        try {
            if (!ShizukuBatterySource.isAvailable()) {
                Toast.makeText(this, "未检测到 Shizuku，请先安装并启动 Shizuku", Toast.LENGTH_LONG).show();
                return;
            }
            if (ShizukuBatterySource.hasPermission()) {
                ShizukuBatterySource.bindIfPossible();
                Toast.makeText(this, "已授权，正在连接只读增强读取", Toast.LENGTH_SHORT).show();
            } else {
                ShizukuBatterySource.requestPermission(this);
                Toast.makeText(this, "请在 Shizuku 弹窗中允许本应用", Toast.LENGTH_LONG).show();
            }
            rebuildContent();
        } catch (Throwable t) {
            Toast.makeText(this, "Shizuku 操作失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void setShizukuEnabled(boolean enabled) {
        Prefs prefs = Prefs.get(this);
        if (!enabled) {
            prefs.setShizukuReadEnabled(false);
            ShizukuBatterySource.unbind();
            ChargeEngine.get(this).sampleNow();
            rebuildContent();
            return;
        }
        if (!ShizukuBatterySource.isAvailable()) {
            prefs.setShizukuReadEnabled(false);
            Toast.makeText(this, "未检测到 Shizuku，请先安装并启动", Toast.LENGTH_LONG).show();
            rebuildContent();
            return;
        }
        prefs.setShizukuReadEnabled(true);
        if (ShizukuBatterySource.hasPermission()) {
            ShizukuBatterySource.bindIfPossible();
        } else {
            ShizukuBatterySource.requestPermission(this);
        }
        ChargeEngine.get(this).sampleNow();
        rebuildContent();
    }

    private String dualLabel(int mode) {
        if (mode == Prefs.DUAL_FORCE) return "强制 ×2";
        if (mode == Prefs.DUAL_OFF) return "关闭";
        return "自动（推荐）";
    }
}
