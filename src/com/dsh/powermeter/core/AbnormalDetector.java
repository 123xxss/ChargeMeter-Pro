package com.dsh.powermeter.core;

/**
 * 充电异常判定。为避免插拔瞬间误报，所有判定都要求持续一段时间：
 * - 过温：温度 >= 45℃ 立即提示；
 * - 功率暴跌 / 持续偏低：曾有 >=15W，随后低于峰值 55% 持续 30 秒；
 * - 功率剧烈跳变：20 秒内多次大幅波动，提示检查接口与线材。
 */
public final class AbnormalDetector {

    public static final int NONE = 0;
    public static final int OVERHEAT = 1;
    public static final int POWER_DROP = 2;
    public static final int UNSTABLE = 3;
    public static final int LOW_POWER = 4;

    private static final long LOW_DURATION_MS = 30_000L;
    private static final long WARN_HOLD_MS = 60_000L;
    private static final long UNSTABLE_WINDOW_MS = 20_000L;

    private long lowSince;
    private long dropWarnUntil;
    private long unstableWarnUntil;
    private final java.util.ArrayDeque<Long> unstableEvents = new java.util.ArrayDeque<>();
    private double prevW = -1;
    private boolean dropReported;

    public int update(long nowMs, boolean charging, double watts, double tempC, double sessionPeakW) {
        if (!charging) {
            reset();
            return NONE;
        }
        if (!Double.isNaN(tempC) && tempC >= 45.0) {
            return OVERHEAT;
        }

        if (nowMs < dropWarnUntil) return POWER_DROP;
        if (nowMs < unstableWarnUntil) return UNSTABLE;

        // 功率暴跌 / 持续偏低
        if (sessionPeakW >= 15.0 && watts > 0.05 && watts < sessionPeakW * 0.55) {
            if (lowSince == 0) lowSince = nowMs;
            if (nowMs - lowSince >= LOW_DURATION_MS) {
                lowSince = 0;
                dropWarnUntil = nowMs + WARN_HOLD_MS;
                return POWER_DROP;
            }
        } else {
            lowSince = 0;
        }

        // 剧烈跳变
        if (prevW >= 0 && watts > 0) {
            double threshold = Math.max(3.0, prevW * 0.35);
            if (Math.abs(watts - prevW) > threshold) {
                unstableEvents.addLast(nowMs);
            }
        }
        prevW = watts;
        while (!unstableEvents.isEmpty()
                && nowMs - unstableEvents.peekFirst() > UNSTABLE_WINDOW_MS) {
            unstableEvents.removeFirst();
        }
        if (unstableEvents.size() >= 5) {
            unstableEvents.clear();
            unstableWarnUntil = nowMs + WARN_HOLD_MS;
            return UNSTABLE;
        }
        return NONE;
    }

    public String text(int kind) {
        switch (kind) {
            case OVERHEAT:
                return "充电降温降速中";
            case POWER_DROP:
                return "充电功率偏低";
            case UNSTABLE:
                return "功率波动较大，建议检查接口/线材";
            case LOW_POWER:
                return "充电功率偏低";
            default:
                return null;
        }
    }

    public void reset() {
        lowSince = 0;
        dropWarnUntil = 0;
        unstableWarnUntil = 0;
        unstableEvents.clear();
        prevW = -1;
        dropReported = false;
    }
}
