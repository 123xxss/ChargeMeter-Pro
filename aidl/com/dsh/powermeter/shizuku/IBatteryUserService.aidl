package com.dsh.powermeter.shizuku;

interface IBatteryUserService {
    /** Shizuku 保留的销毁事务号。 */
    void destroy() = 16777114;

    /** "voltage_now;current_now;temp;charge_counter;status;voltage_max"（只读电池白名单）。 */
    String readBattery() = 2;

    /** "thermalMaxC;gpuPercent;cpuLine;cpuFreq"（只读 sysfs）。 */
    String readSystem() = 3;

    /** 固定只读命令 dumpsys batterystats 的原始输出；仅用于实验性精准模式。 */
    String readBatteryStats() = 4;
}
