package com.dsh.powermeter.qa;

import com.dsh.powermeter.core.RawBattery;

/**
 * 测试夹具：构造 {@link RawBattery} 采样。
 *
 * 这里的 status / plugged 常量与 android.os.BatteryManager 完全一致
 * （BATTERY_STATUS_UNKNOWN=1 / CHARGING=2 / DISCHARGING=3 / NOT_CHARGING=4 / FULL=5，
 * BATTERY_PLUGGED_AC=1 / USB=2）。故意写成字面量而不是引用 android.jar，
 * 以保证测试运行时只需要 core 类、不需要 Android 运行时。
 */
final class Fixtures {

    static final int STATUS_UNKNOWN = 1;
    static final int STATUS_CHARGING = 2;
    static final int STATUS_DISCHARGING = 3;
    static final int STATUS_NOT_CHARGING = 4;
    static final int STATUS_FULL = 5;

    static final int PLUGGED_AC = 1;
    static final int PLUGGED_USB = 2;

    private Fixtures() {
    }

    /** 默认基线：50% 电量、4.3V、30℃、支持电流、未知充电器功率。 */
    static RawBattery.Builder base() {
        return RawBattery.builder()
                .timestampMs(0L)
                .level(50)
                .temperatureDeciC(300)
                .voltageMv(4300)
                .currentRaw(0L)
                .currentSupported(true)
                .powerMaxW(0);
    }

    /** 充电中（AC 供电）、默认 4.3V 的采样，电流单位为 µA。 */
    static RawBattery.Builder charging(long currentRaw) {
        return base().status(STATUS_CHARGING).plugged(PLUGGED_AC).currentRaw(currentRaw);
    }

    /** 未插电、放电中的采样，电流单位为 µA。 */
    static RawBattery.Builder discharging(long currentRaw) {
        return base().status(STATUS_DISCHARGING).plugged(0).currentRaw(currentRaw);
    }
}
