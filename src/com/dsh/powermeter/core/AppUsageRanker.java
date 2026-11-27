package com.dsh.powermeter.core;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Process;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 应用使用排行（前台时长占比）与耗电估算。
 *
 * Android 不向普通第三方应用开放 BatteryStats 的精确每应用耗电数据；
 * 这里使用用户主动授予的「使用情况访问」权限，按最近 24 小时前台时长占全部
 * 前台时长的比例，结合当前电池功率给出“估算耗电”，页面必须明确标注为估算。
 * 系统应用也一并列出；不做后台精确耗电的承诺。
 */
public final class AppUsageRanker {

    private static final long WINDOW_MS = 24L * 60L * 60L * 1000L;

    private AppUsageRanker() {
    }

    public static final class Entry {
        public final String packageName;
        public final String label;
        public final boolean system;
        public final long foregroundMs;
        public final double percent;
        public final double estimatedW;

        Entry(String packageName, String label, boolean system, long foregroundMs,
              double percent, double estimatedW) {
            this.packageName = packageName;
            this.label = label;
            this.system = system;
            this.foregroundMs = foregroundMs;
            this.percent = percent;
            this.estimatedW = estimatedW;
        }
    }

    public static final class Result {
        public final boolean hasAccess;
        public final long totalForegroundMs;
        public final List<Entry> entries;
        public final long generatedAt;

        Result(boolean hasAccess, long total, List<Entry> entries) {
            this.hasAccess = hasAccess;
            this.totalForegroundMs = total;
            this.entries = entries;
            this.generatedAt = System.currentTimeMillis();
        }
    }

    public static boolean hasAccess(Context context) {
        try {
            AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (ops == null) return false;
            int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), context.getPackageName());
            if (mode == AppOpsManager.MODE_ALLOWED) return true;
            if (mode == AppOpsManager.MODE_DEFAULT) {
                return context.checkSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
                        == PackageManager.PERMISSION_GRANTED;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void openAccessSettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable t) {
            try {
                Intent fallback = new Intent(Settings.ACTION_SETTINGS);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
            } catch (Throwable ignored) {
            }
        }
    }

    public static Result load(Context context, double batteryWatts) {
        boolean access = hasAccess(context);
        if (!access) return new Result(false, 0, Collections.emptyList());
        long end = System.currentTimeMillis();
        long start = end - WINDOW_MS;
        List<UsageStats> stats = null;
        try {
            UsageStatsManager usm = (UsageStatsManager)
                    context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return new Result(true, 0, Collections.emptyList());
            stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end);
            if (stats == null || stats.isEmpty()) {
                stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end);
            }
        } catch (Throwable ignored) {
        }
        if (stats == null || stats.isEmpty()) {
            return new Result(true, 0, Collections.emptyList());
        }

        PackageManager pm = context.getPackageManager();
        String self = context.getPackageName();
        List<Entry> raw = new ArrayList<>();
        long total = 0;
        for (UsageStats u : stats) {
            if (u == null || u.getPackageName() == null) continue;
            String pkg = u.getPackageName();
            if (pkg.equals(self)) continue;
            long fg = u.getTotalTimeInForeground();
            if (fg <= 0) continue;
            total += fg;
            raw.add(new Entry(pkg, labelOf(pm, pkg), isSystem(pm, pkg), fg, 0, Double.NaN));
        }
        if (total <= 0) return new Result(true, 0, Collections.emptyList());

        List<Entry> out = new ArrayList<>(raw.size());
        for (Entry e : raw) {
            double pct = e.foregroundMs * 100.0 / total;
            double watts = Double.isFinite(batteryWatts) && batteryWatts > 0
                    ? batteryWatts * e.foregroundMs / total : Double.NaN;
            out.add(new Entry(e.packageName, e.label, e.system,
                    e.foregroundMs, pct, watts));
        }
        Collections.sort(out, (a, b) -> Long.compare(b.foregroundMs, a.foregroundMs));
        return new Result(true, total, out);
    }

    private static String labelOf(PackageManager pm, String pkg) {
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            CharSequence label = pm.getApplicationLabel(ai);
            return label == null ? pkg : label.toString();
        } catch (Throwable t) {
            return pkg;
        }
    }

    private static boolean isSystem(PackageManager pm, String pkg) {
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (Throwable t) {
            return false;
        }
    }
}
