package com.dsh.powermeter.core;

/**
 * 归一化后的电池读数。所有单位均为 V / A / W / °C。
 *
 * 符号约定：
 * - currentA > 0 表示电流流入电池，< 0 表示电池对外放电；
 * - chargeW >= 0，仅在充电中有效；
 * - netW 带符号（+ 入电池，- 放电）。
 */
public final class NormalizedReading {

    public final long timestampMs;
    public final boolean valid;
    public final String invalidReason;
    public final boolean plugged;
    public final int level;
    public final ChargeState state;
    public final double voltageV;
    public final double currentA;
    public final double chargeW;
    public final double netW;
    public final int cells;
    public final boolean voltageCorrected;
    public final boolean enhanced;
    public final boolean currentSupported;
    public final double temperatureC;
    public final long chargeCounterUah;
    public final int powerMaxW;
    public final double rawVoltageV;
    public final long rawCurrent;
    public final int unitUsed;
    public final boolean nearZero;

    private NormalizedReading(Builder b) {
        this.timestampMs = b.timestampMs;
        this.valid = b.valid;
        this.invalidReason = b.invalidReason;
        this.plugged = b.plugged;
        this.level = b.level;
        this.state = b.state;
        this.voltageV = b.voltageV;
        this.currentA = b.currentA;
        this.chargeW = b.chargeW;
        this.netW = b.netW;
        this.cells = b.cells;
        this.voltageCorrected = b.voltageCorrected;
        this.enhanced = b.enhanced;
        this.currentSupported = b.currentSupported;
        this.temperatureC = b.temperatureC;
        this.chargeCounterUah = b.chargeCounterUah;
        this.powerMaxW = b.powerMaxW;
        this.rawVoltageV = b.rawVoltageV;
        this.rawCurrent = b.rawCurrent;
        this.unitUsed = b.unitUsed;
        this.nearZero = b.nearZero;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        public long timestampMs = System.currentTimeMillis();
        public boolean valid = false;
        public String invalidReason;
        public boolean plugged = false;
        public int level = -1;
        public ChargeState state = ChargeState.UNPLUGGED;
        public double voltageV = 0;
        public double currentA = 0;
        public double chargeW = 0;
        public double netW = 0;
        public int cells = 1;
        public boolean voltageCorrected = false;
        public boolean enhanced = false;
        public boolean currentSupported = false;
        public double temperatureC = Double.NaN;
        public long chargeCounterUah = -1;
        public int powerMaxW = 0;
        public double rawVoltageV = 0;
        public long rawCurrent = 0;
        public int unitUsed = ChargeNormalizer.UNIT_MICRO;
        public boolean nearZero = false;

        public Builder timestampMs(long v) { timestampMs = v; return this; }
        public Builder valid(boolean v) { valid = v; return this; }
        public Builder invalidReason(String v) { invalidReason = v; return this; }
        public Builder plugged(boolean v) { plugged = v; return this; }
        public Builder level(int v) { level = v; return this; }
        public Builder state(ChargeState v) { state = v; return this; }
        public Builder voltageV(double v) { voltageV = v; return this; }
        public Builder currentA(double v) { currentA = v; return this; }
        public Builder chargeW(double v) { chargeW = v; return this; }
        public Builder netW(double v) { netW = v; return this; }
        public Builder cells(int v) { cells = v; return this; }
        public Builder voltageCorrected(boolean v) { voltageCorrected = v; return this; }
        public Builder enhanced(boolean v) { enhanced = v; return this; }
        public Builder currentSupported(boolean v) { currentSupported = v; return this; }
        public Builder temperatureC(double v) { temperatureC = v; return this; }
        public Builder chargeCounterUah(long v) { chargeCounterUah = v; return this; }
        public Builder powerMaxW(int v) { powerMaxW = v; return this; }
        public Builder rawVoltageV(double v) { rawVoltageV = v; return this; }
        public Builder rawCurrent(long v) { rawCurrent = v; return this; }
        public Builder unitUsed(int v) { unitUsed = v; return this; }
        public Builder nearZero(boolean v) { nearZero = v; return this; }

        public NormalizedReading build() {
            return new NormalizedReading(this);
        }
    }
}
