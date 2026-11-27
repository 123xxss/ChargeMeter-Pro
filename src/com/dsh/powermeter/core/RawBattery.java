package com.dsh.powermeter.core;

/**
 * 一次原始电池采样，全部字段来自系统公开 API 或标准 sysfs 节点，
 * 不做任何口径换算。归一化在 {@link ChargeNormalizer} 中完成。
 */
public final class RawBattery {

    public static final double NO_SYSFS = Double.NaN;

    public final long timestampMs;
    public final int status;
    public final int plugged;
    public final int level;
    public final int voltageMv;
    public final int temperatureDeciC;
    public final long currentRaw;
    public final boolean currentSupported;
    /** 充电器协商/最大功率（W），0 表示未知。来源：MIUI 广播或系统 max_charging_* extras。 */
    public final int powerMaxW;
    public final String quickChargeType;
    public final long chargeCounterUah;

    /** 标准 sysfs 只读增强路径（实验性），NaN 表示不可用。 */
    public final double sysfsVoltageV;
    public final double sysfsCurrentA;
    public final double sysfsTempC;
    public final String sysfsStatus;
    /** 已知双电芯机型提示（来自品牌/机型表），用于自动 ×2。 */
    public final boolean likelyDualCell;
    /** sysfs voltage_max（V，整包最大电压），NaN 表示未知。 */
    public final double sysfsVoltageMaxV;

    private RawBattery(Builder b) {
        this.timestampMs = b.timestampMs;
        this.status = b.status;
        this.plugged = b.plugged;
        this.level = b.level;
        this.voltageMv = b.voltageMv;
        this.temperatureDeciC = b.temperatureDeciC;
        this.currentRaw = b.currentRaw;
        this.currentSupported = b.currentSupported;
        this.powerMaxW = b.powerMaxW;
        this.quickChargeType = b.quickChargeType;
        this.chargeCounterUah = b.chargeCounterUah;
        this.sysfsVoltageV = b.sysfsVoltageV;
        this.sysfsCurrentA = b.sysfsCurrentA;
        this.sysfsTempC = b.sysfsTempC;
        this.sysfsStatus = b.sysfsStatus;
        this.likelyDualCell = b.likelyDualCell;
        this.sysfsVoltageMaxV = b.sysfsVoltageMaxV;
    }

    public boolean isPlugged() {
        return plugged != 0;
    }

    public boolean isChargingStatus() {
        return status == android.os.BatteryManager.BATTERY_STATUS_CHARGING;
    }

    public boolean isFullStatus() {
        return status == android.os.BatteryManager.BATTERY_STATUS_FULL;
    }

    public boolean hasSysfs() {
        return !Double.isNaN(sysfsVoltageV) && sysfsVoltageV > 0.5;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private long timestampMs = System.currentTimeMillis();
        private int status = android.os.BatteryManager.BATTERY_STATUS_UNKNOWN;
        private int plugged = 0;
        private int level = -1;
        private int voltageMv = 0;
        private int temperatureDeciC = Integer.MIN_VALUE;
        private long currentRaw = 0;
        private boolean currentSupported = false;
        private int powerMaxW = 0;
        private String quickChargeType;
        private long chargeCounterUah = -1;
        private double sysfsVoltageV = NO_SYSFS;
        private double sysfsCurrentA = NO_SYSFS;
        private double sysfsTempC = NO_SYSFS;
        private String sysfsStatus;
        private boolean likelyDualCell = false;
        private double sysfsVoltageMaxV = NO_SYSFS;

        public Builder timestampMs(long v) { timestampMs = v; return this; }
        public Builder status(int v) { status = v; return this; }
        public Builder plugged(int v) { plugged = v; return this; }
        public Builder level(int v) { level = v; return this; }
        public Builder voltageMv(int v) { voltageMv = v; return this; }
        public Builder temperatureDeciC(int v) { temperatureDeciC = v; return this; }
        public Builder currentRaw(long v) { currentRaw = v; return this; }
        public Builder currentSupported(boolean v) { currentSupported = v; return this; }
        public Builder powerMaxW(int v) { powerMaxW = v; return this; }
        public Builder quickChargeType(String v) { quickChargeType = v; return this; }
        public Builder chargeCounterUah(long v) { chargeCounterUah = v; return this; }
        public Builder sysfsVoltageV(double v) { sysfsVoltageV = v; return this; }
        public Builder sysfsCurrentA(double v) { sysfsCurrentA = v; return this; }
        public Builder sysfsTempC(double v) { sysfsTempC = v; return this; }
        public Builder sysfsStatus(String v) { sysfsStatus = v; return this; }
        public Builder likelyDualCell(boolean v) { likelyDualCell = v; return this; }
        public Builder sysfsVoltageMaxV(double v) { sysfsVoltageMaxV = v; return this; }

        public RawBattery build() {
            return new RawBattery(this);
        }
    }
}
