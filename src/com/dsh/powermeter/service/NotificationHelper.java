package com.dsh.powermeter.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.dsh.powermeter.MainActivity;
import com.dsh.powermeter.Prefs;
import com.dsh.powermeter.R;
import com.dsh.powermeter.core.ChargeState;
import com.dsh.powermeter.core.PowerSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 通知栏充电卡片的渠道与内容构造。
 *
 * 设计原则：
 * - 只有「真实的、可信的」读数才会上通知栏；读数不可用 / 已暂停 / 已充满 /
 *   净功耗接近零 一律显示对应状态文案，绝不显示误导性的 0.0 W。
 * - 未充电时只显示「充电功率监测中 / 接入电源后自动显示」，不出现任何功率数字。
 * - 文案由 {@link PowerSnapshot} 与 {@link Prefs} 推导，纯计算，不做 I/O。
 */
public final class NotificationHelper {

    private static final String TAG = "PowerNotification";

    /** 通知渠道 id（与 PRD 一致）。 */
    public static final String CHANNEL_ID = "charge_monitor";

    /** 常驻通知 id：前台服务与后续刷新共用同一条通知。 */
    public static final int NOTIFICATION_ID = 0x504D; // "PM"

    private static final int REQ_OPEN_MAIN = 1;
    private static final int REQ_OPEN_SETTINGS = 2;

    /** 低于该功率视为「接近零」，不展示具体数值。 */
    private static final double NEAR_ZERO_W = 0.05;

    /** 正文超过该长度时使用 BigTextStyle，避免被截断。 */
    private static final int BIG_TEXT_THRESHOLD = 16;

    private NotificationHelper() {
    }

    // ------------------------------------------------------------------ 渠道

    /** 创建（或复用）低优先级、无声无振动、锁屏可见的通知渠道。 */
    public static void ensureChannel(Context context) {
        if (context == null) return;
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(context.getString(R.string.channel_desc));
            channel.setSound(null, null);
            channel.enableVibration(false);
            channel.enableLights(false);
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(channel);
        } catch (Throwable t) {
            Log.w(TAG, "创建通知渠道失败：" + t.getClass().getSimpleName());
        }
    }

    // ------------------------------------------------------------------ 内容

    /** 通知文案模型；progress < 0 表示不展示电量进度。 */
    public static final class Content {

        public final String title;
        public final String text;
        public final int progress;

        Content(String title, String text, int progress) {
            this.title = title == null ? "" : title;
            this.text = text == null ? "" : text;
            this.progress = progress;
        }

        /** 去重用键：三者都没变时无需重复刷新通知。 */
        public String key() {
            return title + '\u0000' + text + '\u0000' + progress;
        }
    }

    /** 待机通知：前台服务刚启动、尚未拿到第一帧快照时使用。 */
    public static Content idle() {
        return new Content("充电功率监测中", "接入电源后自动显示", -1);
    }

    /**
     * 最小可用的前台通知：不依赖自定义 action / 额外图标，
     * 用于服务刚启动时先满足 startForeground 的系统时限，再异步换成完整卡片。
     */
    public static Notification buildMinimal(Context context) {
        Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle("充电功率 Pro")
                .setContentText("正在启动监测")
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_STATUS)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setDefaults(0);
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return builder.build();
    }

    /** 根据快照推导通知文案。任何分支都不抛异常，最差回落到待机文案。 */
    public static Content content(Prefs prefs, PowerSnapshot s) {
        if (s == null || prefs == null) return idle();
        if (!prefs.notificationEnabled()) {
            return new Content("充电功率监测中", "通知详情已关闭 · 可在应用内重新开启", -1);
        }

        ChargeState state = s.state == null ? ChargeState.UNPLUGGED : s.state;
        boolean showTemp = prefs.showTemperature();
        boolean showEstimate = prefs.showEstimate();
        boolean showTier = prefs.showTier();

        int level = (s.level >= 0 && s.level <= 100) ? s.level : -1;
        int progress = level;

        List<String> parts = new ArrayList<>(4);
        if (level >= 0) parts.add("电量 " + level + "%");
        if (showTemp && tempUsable(s.temperatureC)) parts.add("温度 " + fmt(s.temperatureC) + "°C");

        String title;
        switch (state) {
            case FULL:
                title = "已充满";
                parts.add("已停止为电池充电");
                break;
            case ABNORMAL:
                title = "充电异常";
                // 异常原因放在正文最前面，优先于电量/温度
                parts.add(0, s.abnormalText != null ? s.abnormalText : "建议检查充电器、线材或接口");
                // PRD：异常降速时功率仍按实际展示，只是用低打扰提醒
                if (s.valid && powerUsable(s.powerW) && s.powerW >= NEAR_ZERO_W) {
                    parts.add("当前 " + fmt(s.powerW) + " W");
                }
                break;
            case PAUSED:
                title = "已暂停充电";
                parts.add("等待恢复充电");
                break;
            case UNAVAILABLE:
                title = "读数不可用";
                parts.add(0, s.invalidReason != null ? s.invalidReason : "当前仅显示充电状态");
                break;
            case CHARGING:
                if (s.realtime) {
                    // 实时功耗口径：净功耗；接近零时给状态文案而不是 0.0 W
                    if (s.nearZero || !powerUsable(s.powerW) || s.powerW < NEAR_ZERO_W) {
                        title = "净功耗接近 0 W";
                        parts.add("电池当前未在入电");
                    } else {
                        title = s.modeLabel + " " + fmt(s.powerW) + " W";
                    }
                } else if (!s.valid || !powerUsable(s.powerW)) {
                    title = "读数不可用";
                    parts.add(0, s.invalidReason != null ? s.invalidReason : "电流读数不可用");
                } else if (s.powerW < NEAR_ZERO_W) {
                    title = "充电中";
                    parts.add("当前入电功率极低");
                } else {
                    String tierLabel = showTier && s.tier != null
                            ? s.tier.label : ChargeState.CHARGING.label;
                    title = fmt(s.powerW) + " W · " + tierLabel;
                }
                if (showEstimate && s.valid && s.estimateValid && s.remainingSeconds > 0) {
                    parts.add(estimateText(s.remainingSeconds));
                }
                break;
            case UNPLUGGED:
            default:
                if (s.realtime && s.currentSupported && powerUsable(s.powerW) && s.powerW >= NEAR_ZERO_W) {
                    // 实时功耗模式下未充电也能看到放电功耗
                    title = "放电功耗 " + fmt(s.powerW) + " W";
                } else {
                    return idle();
                }
                break;
        }

        if (s.accessoryText != null && !s.accessoryText.isEmpty()
                && (state == ChargeState.CHARGING || state == ChargeState.ABNORMAL)) {
            parts.add(s.accessoryText);
        }

        String text = join(parts);
        if (text.isEmpty()) text = "接入电源后自动显示";
        return new Content(title, text, progress);
    }

    // ------------------------------------------------------------ 通知构建

    /** 构建常驻通知：电量进度、锁屏可见、状态分类、静默、仅点击跳转。 */
    public static Notification build(Context context, Content content) {
        PendingIntent openMain = pendingActivity(context, MainActivity.class, REQ_OPEN_MAIN, true);

        Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(content.title)
                .setContentText(content.text)
                .setContentIntent(openMain)
                .setShowWhen(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_STATUS)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setAutoCancel(false)
                // 静默由渠道保证（IMPORTANCE_LOW + 无声音 + 无振动）；
                // 平台 Notification.Builder 没有 setSilent（那是 NotificationCompat 的方法）
                .setDefaults(0);

        if (content.text.length() > BIG_TEXT_THRESHOLD) {
            builder.setStyle(new Notification.BigTextStyle().bigText(content.text));
        }
        if (content.progress >= 0 && content.progress <= 100) {
            builder.setProgress(100, content.progress, false);
        }
        // 前台服务通知立即展示，避免系统延迟显示导致被判定为「未进入前台」
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return builder.build();
    }

    private static PendingIntent pendingActivity(Context context, Class<?> cls,
                                                 int requestCode, boolean launcherStyle) {
        Intent intent;
        if (launcherStyle) {
            intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setClass(context, cls);
        } else {
            intent = new Intent(context, cls);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // ------------------------------------------------------------ 文案工具

    private static boolean powerUsable(double watts) {
        return !Double.isNaN(watts) && !Double.isInfinite(watts);
    }

    private static boolean tempUsable(double celsius) {
        return !Double.isNaN(celsius) && !Double.isInfinite(celsius) && celsius > -50 && celsius < 150;
    }

    private static String fmt(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private static String estimateText(long seconds) {
        long minutes = (seconds + 30) / 60;
        if (minutes < 1) minutes = 1;
        if (minutes < 60) return "预计 " + minutes + " 分钟充满";
        long hours = minutes / 60;
        long rest = minutes % 60;
        if (rest == 0) return "预计 " + hours + " 小时充满";
        return "预计 " + hours + " 小时 " + rest + " 分充满";
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(part);
        }
        return sb.toString();
    }
}
