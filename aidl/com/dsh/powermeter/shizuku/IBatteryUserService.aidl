package com.dsh.powermeter.shizuku;

interface IBatteryUserService {
    /** 返回 "voltage_now;current_now;temp;charge_counter;status"（只读白名单节点）。 */
    String readBattery();
}
