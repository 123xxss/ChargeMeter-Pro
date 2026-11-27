package com.dsh.powermeter.core;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Process;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 Shizuku 只读 `dumpsys batterystats` 的“Estimated power use (mAh)”段。
 *
 * 这是系统 BatteryStats 的估算值，比前台时长占比更接近真实，但：
 * - 不是实时，通常几十秒更新；
 * - 不同 ROM 输出格式/字段可能不同；
 * - 数据会随充电、重启、重置而清空。
 * 解析失败时调用方回退普通 UsageEvents 排行，绝不硬编数据。
 */
public final class PrecisePowerRanker {

    private static final Pattern UID_LINE = Pattern.compile(
            "(?i)^\\s*(?:UID|Uid)\\s+(\\d+)\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)");

    private PrecisePowerRanker() {
    }

    public static final class Entry {
        public final int uid;
        public final String label;
        public final boolean system;
        public final double mah;
        public final double percent;
        public final double estimatedW;

        Entry(int uid, String label, boolean system, double mah,
              double percent, double estimatedW) {
            this.uid = uid;
            this.label = label;
            this.system = system;
            this.mah = mah;
            this.percent = percent;
            this.estimatedW = estimatedW;
        }
    }

    public static final class Result {
        public final boolean available;
        public final String message;
        public final double totalMah;
        public final List<Entry> entries;
        public final long generatedAt;

        Result(boolean available, String message, double totalMah, List<Entry> entries) {
            this.available = available;
            this.message = message == null ? "" : message;
            this.totalMah = totalMah;
            this.entries = entries;
            this.generatedAt = System.currentTimeMillis();
        }
    }

    public static Result parse(Context context, String raw, double batteryWatts) {
        if (context == null || raw == null || raw.trim().isEmpty()) {
            return new Result(false, "没有读取到系统 BatteryStats", 0,
                    Collections.emptyList());
        }
        String[] lines = raw.split("\\r?\\n");
        Map<Integer, Double> values = new LinkedHashMap<>();
        boolean inSection = false;
        int inSectionEntries = 0;
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (!inSection && lower.contains("estimated power use")
                    && lower.contains("mah")) {
                inSection = true;
                continue;
            }
            if (inSection) {
                Matcher m = UID_LINE.matcher(line);
                if (m.find()) {
                    try {
                        int uid = Integer.parseInt(m.group(1));
                        double mah = Double.parseDouble(m.group(2));
                        if (mah > 0) {
                            Double old = values.get(uid);
                            values.put(uid, old == null ? mah : Math.max(old, mah));
                            inSectionEntries++;
                        }
                    } catch (Throwable ignored) {
                    }
                } else if (inSectionEntries > 0 && !line.trim().isEmpty()
                        && !line.startsWith(" ") && !line.startsWith("\t")) {
                    break; // 进入下一段
                }
            }
        }
        // 无法识别段头时，至少扫描所有 UID 行；仍要求有数值才展示。
        if (values.isEmpty()) {
            for (String line : lines) {
                Matcher m = UID_LINE.matcher(line);
                if (!m.find()) continue;
                try {
                    int uid = Integer.parseInt(m.group(1));
                    double mah = Double.parseDouble(m.group(2));
                    if (mah > 0) values.put(uid, Math.max(
                            values.containsKey(uid) ? values.get(uid) : 0, mah));
                } catch (Throwable ignored) {
                }
            }
        }
        if (values.isEmpty()) {
            return new Result(false, "系统 BatteryStats 暂无可解析的估算数据", 0,
                    Collections.emptyList());
        }

        PackageManager pm = context.getPackageManager();
        int selfUid = Process.myUid();
        List<Entry> rawEntries = new ArrayList<>();
        double total = 0;
        for (Map.Entry<Integer, Double> me : values.entrySet()) {
            int uid = me.getKey();
            if (uid == selfUid) continue;
            String label = null;
            boolean system = false;
            try {
                String[] pkgs = pm.getPackagesForUid(uid);
                if (pkgs == null || pkgs.length == 0) continue;
                String pkg = pkgs[0];
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                CharSequence l = pm.getApplicationLabel(ai);
                label = l == null ? pkg : l.toString();
                system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            } catch (Throwable t) {
                continue;
            }
            if (label == null) continue;
            double mah = me.getValue() == null ? 0 : me.getValue();
            if (mah <= 0) continue;
            total += mah;
            rawEntries.add(new Entry(uid, label, system, mah, 0, Double.NaN));
        }
        if (total <= 0 || rawEntries.isEmpty()) {
            return new Result(false, "BatteryStats 中没有可展示的应用耗电", 0,
                    Collections.emptyList());
        }
        List<Entry> out = new ArrayList<>(rawEntries.size());
        for (Entry e : rawEntries) {
            double pct = e.mah * 100.0 / total;
            double watts = Double.isFinite(batteryWatts) && batteryWatts > 0
                    ? batteryWatts * e.mah / total : Double.NaN;
            out.add(new Entry(e.uid, e.label, e.system, e.mah, pct, watts));
        }
        Collections.sort(out, (a, b) -> Double.compare(b.mah, a.mah));
        return new Result(true, "系统估算 mAh", total, out);
    }

    public static String formatMah(double mah) {
        return String.format(Locale.ROOT, "%.2f mAh", mah);
    }
}
