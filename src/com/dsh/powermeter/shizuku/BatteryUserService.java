package com.dsh.powermeter.shizuku;

import java.io.File;
import java.io.FileInputStream;

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
            "/sys/class/power_supply/battery/status"
    };

    public BatteryUserService() {
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
            byte[] buf = new byte[64];
            int n = in.read(buf);
            if (n <= 0) return "";
            return new String(buf, 0, n, "UTF-8").trim();
        } catch (Throwable t) {
            return "";
        }
    }
}
