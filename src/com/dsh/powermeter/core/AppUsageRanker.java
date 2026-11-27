package com.dsh.powermeter.core;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Process;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 最近前台应用排行（默认最近 10 分钟）。
 *
 * 使用 UsageEvents（ACTIVITY_RESUMED/PAUSED）计算窗口内的前台区间，
 * 只列出窗口内真正在前台出现过的应用，避免 queryUsageStats 24 小时聚合
 * 把很久以前启动过、当前早已不在前台的软件也列出来。
 *
 * 精确每应用耗电仍需 BatteryStats 权限；这里按前台时长占比估算并明确标注。
 */
public final class AppUsageRanker {

    /** 最近窗口：15 分钟；只使用事件时间，不再用 24 小时聚合。 */
    public static final long WINDOW_MS = 15L * 60L * 1000L;

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
        public final long windowMs;
        public final String currentPackage;
        public final String currentLabel;
        public final String lastLabel;

        Result(boolean hasAccess, long total, List<Entry> entries,
               long windowMs, String currentPackage, String currentLabel,
               String lastLabel) {
            this.hasAccess = hasAccess;
            this.totalForegroundMs = total;
            this.entries = entries;
            this.generatedAt = System.currentTimeMillis();
            this.windowMs = windowMs;
            this.currentPackage = currentPackage;
            this.currentLabel = currentLabel;
            this.lastLabel = lastLabel;
        }
    }

    public static boolean hasAccess(Context context) {
        try {
            AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (ops == null) return false;
            int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), context.getPackageName());
            if (mode == AppOpsManager.MODE_ALLOWED
                    || mode == AppOpsManager.MODE_FOREGROUND) return true;
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
        if (!access) {
            return new Result(false, 0, Collections.emptyList(),
                    WINDOW_MS, null, null, null);
        }
        long end = System.currentTimeMillis();
        long start = end - WINDOW_MS;
        UsageEvents events = null;
        try {
            UsageStatsManager usm = (UsageStatsManager)
                    context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm != null) events = usm.queryEvents(start, end);
        } catch (Throwable ignored) {
        }
        if (events == null) {
            return new Result(true, 0, Collections.emptyList(), WINDOW_MS, null, null, null);
        }

        String self = context.getPackageName();
        Map<String, Long> totals = new HashMap<>();
        String current = null;
        String lastForeground = null;
        long currentStart = 0;
        UsageEvents.Event e = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            try {
                events.getNextEvent(e);
            } catch (Throwable t) {
                break;
            }
            String pkg = e.getPackageName();
            if (pkg == null) continue;
            long ts = Math.max(start, Math.min(end, e.getTimeStamp()));
            // 打开本应用时先结束上一个应用的前台区间；本应用自身不参与排行。
            if (pkg.equals(self)) {
                if (current != null) {
                    add(totals, current, Math.max(0, ts - currentStart));
                    current = null;
                    currentStart = 0;
                }
                continue;
            }
            int type = e.getEventType();
            boolean foreground = type == UsageEvents.Event.ACTIVITY_RESUMED
                    || type == UsageEvents.Event.MOVE_TO_FOREGROUND;
            boolean background = type == UsageEvents.Event.ACTIVITY_PAUSED
                    || type == UsageEvents.Event.MOVE_TO_BACKGROUND
                    || type == UsageEvents.Event.ACTIVITY_STOPPED;
            if (foreground) {
                lastForeground = pkg;
                if (current != null && !current.equals(pkg)) {
                    add(totals, current, Math.max(0, ts - currentStart));
                }
                if (current == null || !current.equals(pkg) || currentStart == 0) {
                    current = pkg;
                    currentStart = ts;
                }
            } else if (background && pkg.equals(current)) {
                add(totals, pkg, Math.max(0, ts - currentStart));
                current = null;
                currentStart = 0;
            }
        }
        if (current != null) {
            add(totals, current, Math.max(0, end - currentStart));
        }

        PackageManager pm = context.getPackageManager();
        String lastLabel = lastForeground == null ? null : labelOf(pm, lastForeground);
        List<Entry> raw = new ArrayList<>();
        long total = 0;
        for (Map.Entry<String, Long> me : totals.entrySet()) {
            if (self.equals(me.getKey())) continue;
            long ms = me.getValue() == null ? 0 : me.getValue();
            if (ms < 500) continue; // 过滤瞬间切前台
            total += ms;
            raw.add(new Entry(me.getKey(), labelOf(pm, me.getKey()),
                    isSystem(pm, me.getKey()), ms, 0, Double.NaN));
        }
        // 事件为空/权限受限时，使用系统 lastTimeUsed 过滤的备用数据；
        // 只保留最近 15 分钟内使用过的应用，绝不再按 24 小时聚合全部列出。
        if (total <= 0) {
            try {
                android.app.usage.UsageStatsManager usm = (android.app.usage.UsageStatsManager)
                        context.getSystemService(Context.USAGE_STATS_SERVICE);
                List<android.app.usage.UsageStats> stats = usm == null ? null
                        : usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                        start, end);
                if (stats != null) {
                    for (android.app.usage.UsageStats u : stats) {
                        if (u == null || u.getPackageName() == null
                                || u.getPackageName().equals(self)) continue;
                        if (u.getLastTimeUsed() < start) continue;
                        long ms = Math.min(WINDOW_MS, u.getTotalTimeInForeground());
                        if (ms < 500) continue;
                        total += ms;
                        raw.add(new Entry(u.getPackageName(), labelOf(pm, u.getPackageName()),
                                isSystem(pm, u.getPackageName()), ms, 0, Double.NaN));
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        if (total <= 0) {
            return new Result(true, 0, Collections.emptyList(), WINDOW_MS, current,
                    current == null ? null : labelOf(pm, current), lastLabel);
        }
        List<Entry> out = new ArrayList<>(raw.size());
        for (Entry x : raw) {
            double pct = x.foregroundMs * 100.0 / total;
            double watts = Double.isFinite(batteryWatts) && batteryWatts > 0
                    ? batteryWatts * x.foregroundMs / total : Double.NaN;
            out.add(new Entry(x.packageName, x.label, x.system,
                    x.foregroundMs, pct, watts));
        }
        Collections.sort(out, (a, b) -> Long.compare(b.foregroundMs, a.foregroundMs));
        String currentLabel = current == null ? null : labelOf(pm, current);
        return new Result(true, total, out, WINDOW_MS, current, currentLabel, lastLabel);
    }

    private static void add(Map<String, Long> map, String pkg, long ms) {
        if (pkg == null || ms <= 0) return;
        Long old = map.get(pkg);
        map.put(pkg, (old == null ? 0 : old) + ms);
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
