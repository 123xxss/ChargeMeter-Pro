package com.dsh.powermeter.core;

/** 一帧可供 UI / 通知栏直接消费的展示快照。所有字段均已完成归一化与平滑。 */
public final class PowerSnapshot {

    public final long timestampMs;

    public final boolean masterEnabled;
    public final boolean notificationsAllowed;
    public final boolean realtime;

    public final boolean plugged;
    public final boolean charging;
    public final ChargeState state;
    public final ChargeTier tier;
    public final boolean valid;
    public final boolean nearZero;

    /** 主数值（按当前模式：入电池功率 / 净功耗 / 放电功耗）。非展示场景为 0。 */
    public final double powerW;
    public final String modeLabel;
    public final double chargeW;
    public final double netW;

    public final double voltageV;
    public final double currentA;
    public final double temperatureC;
    public final int level;
    public final int cells;
    public final boolean voltageCorrected;

    public final double peakW;
    public final long remainingSeconds;
    public final boolean estimateValid;

    public final String sourceLabel;
    public final boolean enhanced;
    public final boolean currentSupported;
    public final String invalidReason;

    public final String abnormalText;
    public final String accessoryText;

    public final boolean sessionActive;
    public final long sessionDurationMs;
    public final double sessionAvgW;
    public final double sessionPeakW;
    public final SessionTracker.Summary lastSession;

    public final boolean showTemperature;
    public final boolean showEstimate;
    public final boolean showTier;

    public final int powerMaxW;
    public final int chargeCounterUahRaw; // 仅调试/详情用，不做长期存储
    /** CPU/GPU/内存/温度等系统状态；屏幕关闭或不可用时为 null。 */
    public final SystemStats systemStats;

    private PowerSnapshot(Builder b) {
        this.timestampMs = b.timestampMs;
        this.masterEnabled = b.masterEnabled;
        this.notificationsAllowed = b.notificationsAllowed;
        this.realtime = b.realtime;
        this.plugged = b.plugged;
        this.charging = b.charging;
        this.state = b.state;
        this.tier = b.tier;
        this.valid = b.valid;
        this.nearZero = b.nearZero;
        this.powerW = b.powerW;
        this.modeLabel = b.modeLabel;
        this.chargeW = b.chargeW;
        this.netW = b.netW;
        this.voltageV = b.voltageV;
        this.currentA = b.currentA;
        this.temperatureC = b.temperatureC;
        this.level = b.level;
        this.cells = b.cells;
        this.voltageCorrected = b.voltageCorrected;
        this.peakW = b.peakW;
        this.remainingSeconds = b.remainingSeconds;
        this.estimateValid = b.estimateValid;
        this.sourceLabel = b.sourceLabel;
        this.enhanced = b.enhanced;
        this.currentSupported = b.currentSupported;
        this.invalidReason = b.invalidReason;
        this.abnormalText = b.abnormalText;
        this.accessoryText = b.accessoryText;
        this.sessionActive = b.sessionActive;
        this.sessionDurationMs = b.sessionDurationMs;
        this.sessionAvgW = b.sessionAvgW;
        this.sessionPeakW = b.sessionPeakW;
        this.lastSession = b.lastSession;
        this.showTemperature = b.showTemperature;
        this.showEstimate = b.showEstimate;
        this.showTier = b.showTier;
        this.powerMaxW = b.powerMaxW;
        this.chargeCounterUahRaw = b.chargeCounterUahRaw;
        this.systemStats = b.systemStats;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        public long timestampMs = System.currentTimeMillis();
        public boolean masterEnabled = true;
        public boolean notificationsAllowed = true;
        public boolean realtime = false;
        public boolean plugged = false;
        public boolean charging = false;
        public ChargeState state = ChargeState.UNPLUGGED;
        public ChargeTier tier = ChargeTier.NORMAL;
        public boolean valid = false;
        public boolean nearZero = false;
        public double powerW = 0;
        public String modeLabel = "入电池功率";
        public double chargeW = 0;
        public double netW = 0;
        public double voltageV = 0;
        public double currentA = 0;
        public double temperatureC = Double.NaN;
        public int level = -1;
        public int cells = 1;
        public boolean voltageCorrected = false;
        public double peakW = 0;
        public long remainingSeconds = -1;
        public boolean estimateValid = false;
        public String sourceLabel = "标准读取";
        public boolean enhanced = false;
        public boolean currentSupported = false;
        public String invalidReason;
        public String abnormalText;
        public String accessoryText;
        public boolean sessionActive = false;
        public long sessionDurationMs = 0;
        public double sessionAvgW = 0;
        public double sessionPeakW = 0;
        public SessionTracker.Summary lastSession;
        public boolean showTemperature = true;
        public boolean showEstimate = true;
        public boolean showTier = true;
        public int powerMaxW = 0;
        public int chargeCounterUahRaw = -1;
        public SystemStats systemStats;

        public Builder timestampMs(long v) { timestampMs = v; return this; }
        public Builder masterEnabled(boolean v) { masterEnabled = v; return this; }
        public Builder notificationsAllowed(boolean v) { notificationsAllowed = v; return this; }
        public Builder realtime(boolean v) { realtime = v; return this; }
        public Builder plugged(boolean v) { plugged = v; return this; }
        public Builder charging(boolean v) { charging = v; return this; }
        public Builder state(ChargeState v) { state = v; return this; }
        public Builder tier(ChargeTier v) { tier = v; return this; }
        public Builder valid(boolean v) { valid = v; return this; }
        public Builder nearZero(boolean v) { nearZero = v; return this; }
        public Builder powerW(double v) { powerW = v; return this; }
        public Builder modeLabel(String v) { modeLabel = v; return this; }
        public Builder chargeW(double v) { chargeW = v; return this; }
        public Builder netW(double v) { netW = v; return this; }
        public Builder voltageV(double v) { voltageV = v; return this; }
        public Builder currentA(double v) { currentA = v; return this; }
        public Builder temperatureC(double v) { temperatureC = v; return this; }
        public Builder level(int v) { level = v; return this; }
        public Builder cells(int v) { cells = v; return this; }
        public Builder voltageCorrected(boolean v) { voltageCorrected = v; return this; }
        public Builder peakW(double v) { peakW = v; return this; }
        public Builder remainingSeconds(long v) { remainingSeconds = v; return this; }
        public Builder estimateValid(boolean v) { estimateValid = v; return this; }
        public Builder sourceLabel(String v) { sourceLabel = v; return this; }
        public Builder enhanced(boolean v) { enhanced = v; return this; }
        public Builder currentSupported(boolean v) { currentSupported = v; return this; }
        public Builder invalidReason(String v) { invalidReason = v; return this; }
        public Builder abnormalText(String v) { abnormalText = v; return this; }
        public Builder accessoryText(String v) { accessoryText = v; return this; }
        public Builder sessionActive(boolean v) { sessionActive = v; return this; }
        public Builder sessionDurationMs(long v) { sessionDurationMs = v; return this; }
        public Builder sessionAvgW(double v) { sessionAvgW = v; return this; }
        public Builder sessionPeakW(double v) { sessionPeakW = v; return this; }
        public Builder lastSession(SessionTracker.Summary v) { lastSession = v; return this; }
        public Builder showTemperature(boolean v) { showTemperature = v; return this; }
        public Builder showEstimate(boolean v) { showEstimate = v; return this; }
        public Builder showTier(boolean v) { showTier = v; return this; }
        public Builder powerMaxW(int v) { powerMaxW = v; return this; }
        public Builder chargeCounterUahRaw(int v) { chargeCounterUahRaw = v; return this; }
        public Builder systemStats(SystemStats v) { systemStats = v; return this; }

        public PowerSnapshot build() {
            return new PowerSnapshot(this);
        }
    }
}
