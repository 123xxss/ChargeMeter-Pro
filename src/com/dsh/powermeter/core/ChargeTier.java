package com.dsh.powermeter.core;

/** PRD 2.1 充电档位。默认阈值 5W / 30W，可由设置覆盖。 */
public enum ChargeTier {
    NORMAL("普通充电"),
    FAST("快速充电"),
    SUPER("超级快充");

    public final String label;

    ChargeTier(String label) {
        this.label = label;
    }

    public static ChargeTier of(double watts, double lowWatts, double highWatts) {
        if (watts >= highWatts) return SUPER;
        if (watts >= lowWatts) return FAST;
        return NORMAL;
    }
}
