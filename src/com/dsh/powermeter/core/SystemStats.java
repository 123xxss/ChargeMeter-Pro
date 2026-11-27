package com.dsh.powermeter.core;

import android.app.ActivityManager;
import android.content.Context;
import android.os.StatFs;

import com.dsh.powermeter.shizuku.ShizukuBatterySource;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.util.Locale;

/**
 * 本机系统状态采集：CPU / GPU / 内存 / 存储 / 温度。
 *
 * 说明：
 * - CPU 使用率来自 /proc/stat 两次采样差值，无需权限（部分 ROM 可能限制读取）；
 * - 内存来自 ActivityManager.MemoryInfo；
 * - 存储来自 StatFs；
 * - GPU 使用率没有公开 API，只能尝试只读常见 sysfs 节点；
 * - 温度读取 /sys/class/thermal/thermal_zone 的只读节点，普通应用可能无权限；
 * - 所有项读取失败都返回 NaN，由 UI 显示“--/未开放”，绝不伪造数据。
 */
public final class SystemStats {

    public final double cpuPercent;      // 0-100, NaN unknown
    public final double cpuFreqGhz;      // NaN unknown
    public final int cpuCores;
    public final long ramAvailableMb;
    public final long ramTotalMb;
    public final double ramUsedPercent;
    public final double storageFreeGb;
    public final double storageTotalGb;
    public final double gpuPercent;      // NaN unknown/unsupported
    public final double thermalMaxC;     // NaN unknown
    public final String gpuSource;       // nullable
    public final String thermalSource;   // nullable

    private static long prevTotal;
    private static long prevIdle;

    private SystemStats(Builder b) {
        this.cpuPercent = b.cpuPercent;
        this.cpuFreqGhz = b.cpuFreqGhz;
        this.cpuCores = b.cpuCores;
        this.ramAvailableMb = b.ramAvailableMb;
        this.ramTotalMb = b.ramTotalMb;
        this.ramUsedPercent = b.ramUsedPercent;
        this.storageFreeGb = b.storageFreeGb;
        this.storageTotalGb = b.storageTotalGb;
        this.gpuPercent = b.gpuPercent;
        this.thermalMaxC = b.thermalMaxC;
        this.gpuSource = b.gpuSource;
        this.thermalSource = b.thermalSource;
    }

    public static SystemStats collect(Context context) {
        Builder b = new Builder();
        b.cpuCores = Runtime.getRuntime().availableProcessors();
        readCpu(b);
        readMemory(context, b);
        readStorage(context, b);
        readGpu(b);
        readThermal(b);
        if (Double.isNaN(b.gpuPercent) || Double.isNaN(b.thermalMaxC)
                || Double.isNaN(b.cpuPercent)) {
            try {
                ShizukuBatterySource.SystemValues v = ShizukuBatterySource.readSystem();
                if (v != null) {
                    if (Double.isNaN(b.gpuPercent) && !Double.isNaN(v.gpuPercent)) {
                        b.gpuPercent = v.gpuPercent;
                        b.gpuSource = "shizuku";
                    }
                    if (Double.isNaN(b.thermalMaxC) && !Double.isNaN(v.thermalC)) {
                        b.thermalMaxC = v.thermalC;
                        b.thermalSource = "shizuku";
                    }
                    if (Double.isNaN(b.cpuPercent) && v.cpuLine != null) {
                        parseCpuLine(v.cpuLine, b);
                    }
                    if (Double.isNaN(b.cpuFreqGhz) && !Double.isNaN(v.cpuFreqGhz)) {
                        b.cpuFreqGhz = v.cpuFreqGhz;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return new SystemStats(b);
    }

    private static void readCpu(Builder b) {
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/stat"))) {
            String line = r.readLine();
            if (line == null || !line.startsWith("cpu")) return;
            String[] p = line.trim().split("\\s+");
            if (p.length < 5) return;
            long total = 0;
            for (int i = 1; i < p.length && i <= 8; i++) {
                total += Long.parseLong(p[i]);
            }
            long idle = Long.parseLong(p[4]);
            if (p.length > 5) idle += Long.parseLong(p[5]); // iowait
            if (prevTotal > 0 && total > prevTotal) {
                long dt = total - prevTotal;
                long di = Math.max(0, idle - prevIdle);
                b.cpuPercent = Math.max(0, Math.min(100,
                        (dt - di) * 100.0 / dt));
            } else {
                b.cpuPercent = 0;
            }
            prevTotal = total;
            prevIdle = idle;
        } catch (Throwable ignored) {
        }
        // 当前频率：取第一个可读核，单位 kHz → GHz
        for (int i = 0; i < Math.max(1, b.cpuCores); i++) {
            String path = "/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq";
            String v = readText(path);
            if (v != null) {
                try {
                    b.cpuFreqGhz = Double.parseDouble(v.trim()) / 1_000_000.0;
                    break;
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void parseCpuLine(String line, Builder b) {
        try {
            String[] p = line.trim().split("\s+");
            if (p.length < 5) return;
            long total = 0;
            for (int i = 1; i < p.length && i <= 8; i++) total += Long.parseLong(p[i]);
            long idle = Long.parseLong(p[4]);
            if (p.length > 5) idle += Long.parseLong(p[5]);
            if (prevTotal > 0 && total > prevTotal) {
                long dt = total - prevTotal;
                long di = Math.max(0, idle - prevIdle);
                b.cpuPercent = Math.max(0, Math.min(100, (dt - di) * 100.0 / dt));
            } else {
                b.cpuPercent = 0;
            }
            prevTotal = total;
            prevIdle = idle;
        } catch (Throwable ignored) {
        }
    }

    private static void readMemory(Context context, Builder b) {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                b.ramTotalMb = mi.totalMem / (1024L * 1024L);
                b.ramAvailableMb = mi.availMem / (1024L * 1024L);
                if (mi.totalMem > 0) {
                    b.ramUsedPercent = Math.max(0, Math.min(100,
                            (mi.totalMem - mi.availMem) * 100.0 / mi.totalMem));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void readStorage(Context context, Builder b) {
        try {
            StatFs fs = new StatFs(context.getFilesDir().getAbsolutePath());
            long total = fs.getTotalBytes();
            long free = fs.getAvailableBytes();
            b.storageTotalGb = total / 1_073_741_824.0;
            b.storageFreeGb = free / 1_073_741_824.0;
        } catch (Throwable ignored) {
        }
    }

    private static void readGpu(Builder b) {
        // Qualcomm: "busy total"
        String[] twoValuePaths = {
                "/sys/class/kgsl/kgsl-3d0/gpubusy",
                "/sys/class/kgsl/kgsl-3d0/gpu_busy"
        };
        for (String path : twoValuePaths) {
            String s = readText(path);
            if (s == null) continue;
            try {
                String[] p = s.trim().split("\\s+");
                if (p.length >= 2) {
                    double busy = Double.parseDouble(p[0]);
                    double total = Double.parseDouble(p[1]);
                    if (total > 0) {
                        b.gpuPercent = Math.max(0, Math.min(100, busy * 100.0 / total));
                        b.gpuSource = path;
                        return;
                    }
                } else if (p.length == 1) {
                    double v = Double.parseDouble(p[0]);
                    b.gpuPercent = Math.max(0, Math.min(100, v));
                    b.gpuSource = path;
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
        String[] percentPaths = {
                "/sys/kernel/gpu/gpu_busy",
                "/sys/class/drm/card0/device/gpu_busy_percent",
                "/sys/class/devfreq/gpufreq/load",
                "/sys/class/devfreq/5000000.mali/load"
        };
        for (String path : percentPaths) {
            String s = readText(path);
            if (s == null) continue;
            try {
                double v = Double.parseDouble(s.trim());
                if (v >= 0 && v <= 100) {
                    b.gpuPercent = v;
                    b.gpuSource = path;
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static void readThermal(Builder b) {
        double max = Double.NaN;
        String source = null;
        for (int i = 0; i <= 30; i++) {
            String raw = readText("/sys/class/thermal/thermal_zone" + i + "/temp");
            if (raw == null) continue;
            try {
                double v = Double.parseDouble(raw.trim());
                double c;
                if (v > 2_000) c = v / 1_000.0;
                else if (v > 200) c = v / 10.0;
                else c = v;
                if (c > -20 && c < 120 && (Double.isNaN(max) || c > max)) {
                    max = c;
                    source = "thermal_zone" + i;
                }
            } catch (Throwable ignored) {
            }
        }
        b.thermalMaxC = max;
        b.thermalSource = source;
    }

    private static String readText(String path) {
        File f = new File(path);
        if (!f.isFile() || !f.canRead()) return null;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[64];
            int n = in.read(buf);
            if (n <= 0) return null;
            return new String(buf, 0, n, "UTF-8").trim();
        } catch (Throwable t) {
            return null;
        }
    }

    public String cpuSummary() {
        if (Double.isNaN(cpuPercent)) return "--";
        String s = String.format(Locale.ROOT, "%.0f%%", cpuPercent);
        if (!Double.isNaN(cpuFreqGhz)) {
            s += String.format(Locale.ROOT, " · %.2fGHz", cpuFreqGhz);
        }
        return s;
    }

    public String ramSummary() {
        if (ramTotalMb <= 0) return "--";
        return String.format(Locale.ROOT, "%.1f/%.0fGB", ramAvailableMb / 1024.0,
                ramTotalMb / 1024.0);
    }

    public String gpuSummary() {
        if (Double.isNaN(gpuPercent)) return "--";
        return String.format(Locale.ROOT, "%.0f%%", gpuPercent);
    }

    public String thermalSummary() {
        if (Double.isNaN(thermalMaxC)) return "--";
        return String.format(Locale.ROOT, "%.1f°C", thermalMaxC);
    }

    public String storageSummary() {
        if (storageTotalGb <= 0) return "--";
        return String.format(Locale.ROOT, "%.1fGB 可用", storageFreeGb);
    }

    private static final class Builder {
        double cpuPercent = Double.NaN;
        double cpuFreqGhz = Double.NaN;
        int cpuCores;
        long ramAvailableMb;
        long ramTotalMb;
        double ramUsedPercent = Double.NaN;
        double storageFreeGb = Double.NaN;
        double storageTotalGb = Double.NaN;
        double gpuPercent = Double.NaN;
        double thermalMaxC = Double.NaN;
        String gpuSource;
        String thermalSource;
    }
}
