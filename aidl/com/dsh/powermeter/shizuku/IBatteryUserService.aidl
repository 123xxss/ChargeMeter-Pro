package com.dsh.powermeter.shizuku;

interface IBatteryUserService {
    /** Shizuku 保留的销毁事务号，用于停止 UserService。 */
    void destroy() = 16777114;

    /** 返回 "voltage_now;current_now;temp;charge_counter;status;voltage_max"（只读白名单节点）。 */
    String readBattery() = 2;
}
