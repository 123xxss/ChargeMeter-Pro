package com.dsh.powermeter.shizuku;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * Shizuku UserService：在 shell/root 身份下只读标准 sysfs 电池节点。
 *
 * 安全边界：
 * - 不接受调用方传入路径，只读取写死的白名单；
 * - 只读 FileInputStream，不写文件、不执行 shell、不改 settings；
 * - 返回内容限制长度，异常返回空串；
 * - 由应用自身的 Shizuku 授权保护，未授权不会启动。
 */
public class BatteryUserService extends IBatteryUserService.Stub {

    private static final String[] PATHS = {
            "/sys/class/power_supply/battery/voltage_now",
            "/sys/class/power_supply/battery/current_now",
            "/sys/class/power_supply/battery/temp",
            "/sys/class/power_supply/battery/charge_counter",
            "/sys/class/power_supply/battery/status",
            "/sys/class/power_supply/battery/voltage_max"
    };

    public BatteryUserService() {
    }

    @Override
    public void destroy() {
        // Shizuku 停止 UserService 时调用；只结束本进程，不改系统状态。
        try {
            System.exit(0);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public String readSystem() {
        double thermal = Double.NaN;
        double gpu = Double.NaN;
        for (int i = 0; i <= 30; i++) {
            String raw = read("/sys/class/thermal/thermal_zone" + i + "/temp");
            if (raw.isEmpty()) continue;
            try {
                double v = Double.parseDouble(raw);
                double c = v > 2_000 ? v / 1_000.0 : (v > 200 ? v / 10.0 : v);
                if (c > -20 && c < 120 && (Double.isNaN(thermal) || c > thermal)) thermal = c;
            } catch (Throwable ignored) {
            }
        }
        String[] gpuPaths = {
                "/sys/class/kgsl/kgsl-3d0/gpubusy",
                "/sys/kernel/gpu/gpu_busy",
                "/sys/class/drm/card0/device/gpu_busy_percent"
        };
        for (String path : gpuPaths) {
            String raw = read(path);
            if (raw.isEmpty()) continue;
            try {
                String[] p = raw.split("\\s+");
                if (p.length >= 2) {
                    double busy = Double.parseDouble(p[0]);
                    double total = Double.parseDouble(p[1]);
                    if (total > 0) {
                        gpu = Math.max(0, Math.min(100, busy * 100.0 / total));
                        break;
                    }
                } else {
                    double v = Double.parseDouble(p[0]);
                    if (v >= 0 && v <= 100) {
                        gpu = v;
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        String cpuLine = read("/proc/stat");
        if (cpuLine != null && !cpuLine.isEmpty()) {
            int nl = cpuLine.indexOf('\n');
            if (nl > 0) cpuLine = cpuLine.substring(0, nl);
        } else {
            cpuLine = "";
        }
        String freq = read("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq");
        if (freq == null) freq = "";
        return thermal + ";" + gpu + ";" + cpuLine + ";" + freq;
    }

    @Override
    public String readBatteryStats() {
        // 只执行一条固定、只读的 dumpsys 命令；不接受调用方参数，不写文件。
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder("dumpsys", "batterystats");
            builder.redirectErrorStream(true);
            process = builder.start();
            final Process p = process;
            final ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
            Thread reader = new Thread(() -> {
                try (InputStream in = p.getInputStream()) {
                    byte[] buf = new byte[8192];
                    int n;
                    int total = 0;
                    while ((n = in.read(buf)) != -1 && total < 700_000) {
                        out.write(buf, 0, n);
                        total += n;
                    }
                } catch (Throwable ignored) {
                }
            }, "batterystats-reader");
            reader.setDaemon(true);
            reader.start();
            boolean finished = process.waitFor(20, TimeUnit.SECONDS);
            if (!finished) process.destroy();
            try { reader.join(1500); } catch (Throwable ignored) { }
            String result = out.toString("UTF-8");
            return result.length() > 700_000 ? result.substring(0, 700_000) : result;
        } catch (Throwable t) {
            return "";
        } finally {
            if (process != null) {
                try { process.destroy(); } catch (Throwable ignored) { }
            }
        }
    }

    @Override
    public String readBattery() {
        StringBuilder sb = new StringBuilder(96);
        for (int i = 0; i < PATHS.length; i++) {
            if (i > 0) sb.append(';');
            sb.append(read(PATHS[i]));
        }
        return sb.toString();
    }

    private String read(String path) {
        File f = new File(path);
        if (!f.isFile() || !f.canRead()) return "";
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[256];
            int n = in.read(buf);
            if (n <= 0) return "";
            return new String(buf, 0, n, "UTF-8").trim();
        } catch (Throwable t) {
            return "";
        }
    }
}
