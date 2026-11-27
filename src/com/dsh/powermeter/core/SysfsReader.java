package com.dsh.powermeter.core;

import java.io.File;
import java.io.FileInputStream;

/**
 * 只读增强读取：仅访问 Android 标准 sysfs 电池节点白名单。
 *
 * 安全边界：
 * - 只读，不写文件、不执行 shell、不改 settings；
 * - 路径写死白名单，不接受外部传入路径；
 * - 任何节点不可读/格式异常都静默回退标准 BatteryManager 路径；
 * - 不读取设备唯一标识。
 *
 * 该方法在普通应用 UID 下通常只能读到一部分节点（SELinux 限制），
 * 因此定位为「实验性增强」，默认关闭，读不到也不会影响核心功能。
 */
public final class SysfsReader {

    private static final String[] BASE_DIRS = {
            "/sys/class/power_supply/battery",
            "/sys/class/power_supply/bms"
    };

    public static final class Values {
        public double voltageV = Double.NaN;
        public double currentA = Double.NaN;
        public double tempC = Double.NaN;
        public String status;
        public int chargeCounterUah = -1;
        public double voltageMaxV = Double.NaN;
    }

    private SysfsReader() {
    }

    public static Values read() {
        for (String base : BASE_DIRS) {
            Values v = readBase(base);
            if (v != null && !Double.isNaN(v.voltageV)) return v;
        }
        return null;
    }

    private static Values readBase(String base) {
        File dir = new File(base);
        if (!dir.isDirectory()) return null;
        Values v = new Values();

        String voltage = readFile(base + "/voltage_now");
        if (voltage != null) {
            double raw = parseDouble(voltage);
            if (raw > 100_000) {
                v.voltageV = raw / 1_000_000.0;   // 标准 µV
            } else if (raw > 1_000) {
                v.voltageV = raw / 1_000.0;       // 少数节点上报 mV
            } else if (raw > 1) {
                v.voltageV = raw;                 // 少数节点上报 V
            }
        }

        String voltageMax = readFile(base + "/voltage_max");
        if (voltageMax != null) {
            double raw = parseDouble(voltageMax);
            if (raw > 100_000) v.voltageMaxV = raw / 1_000_000.0;
            else if (raw > 1_000) v.voltageMaxV = raw / 1_000.0;
            else if (raw > 1) v.voltageMaxV = raw;
        }

        String current = readFile(base + "/current_now");
        if (current != null) {
            double raw = parseDouble(current);
            // sysfs 标准单位是 µA；个别节点用 mA，按数量级兜底。
            if (Math.abs(raw) > 30_000_000) {
                v.currentA = raw / 1_000.0;       // 明显是 mA
            } else {
                v.currentA = raw / 1_000_000.0;
            }
        }

        String temp = readFile(base + "/temp");
        if (temp != null) {
            double raw = parseDouble(temp);
            if (raw > 2_000) {
                v.tempC = raw / 1_000.0;
            } else if (raw > 200) {
                v.tempC = raw / 10.0;
            } else if (raw > -50 && raw < 120) {
                v.tempC = raw;
            }
        }

        String counter = readFile(base + "/charge_counter");
        if (counter != null) {
            double raw = parseDouble(counter);
            if (raw > 0 && raw < 100_000_000) {
                v.chargeCounterUah = (int) raw;   // 约 100Ah 以内，防溢出
            }
        }

        v.status = readFile(base + "/status");
        return v;
    }

    private static String readFile(String path) {
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

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Throwable t) {
            return Double.NaN;
        }
    }
}
