package com.dsh.powermeter.shizuku;

interface IBatteryUserService {
    /** Shizuku 保留的销毁事务号。 */
    void destroy() = 16777114;

    /** "voltage_now;current_now;temp;charge_counter;status;voltage_max"（只读电池白名单）。 */
    String readBattery() = 2;

    /** "thermalMaxC;gpuPercent"（只读 thermal/GPU sysfs，不可读返回空/nan）。 */
    String readSystem() = 3;
}
