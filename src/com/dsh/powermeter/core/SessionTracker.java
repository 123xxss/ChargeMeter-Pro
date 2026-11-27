package com.dsh.powermeter.core;

/** 本次充电会话统计（仅内存 + 结束时写入本地摘要，不做长期历史）。 */
public final class SessionTracker {

    private boolean active;
    private long startMs;
    private int startLevel = -1;
    private double sumW;
    private int count;
    private double peakW;

    /**
     * @return 本次充电是否刚结束（用于生成摘要）
     */
    public boolean update(boolean charging, double watts, int level, long nowMs) {
        if (charging && watts > 0.05) {
            if (!active) {
                active = true;
                startMs = nowMs;
                startLevel = level;
                sumW = 0;
                count = 0;
                peakW = 0;
            }
            sumW += watts;
            count++;
            if (watts > peakW) peakW = watts;
            return false;
        }
        if (active) {
            active = false;
            return true;
        }
        return false;
    }

    public boolean active() {
        return active;
    }

    public long durationMs(long nowMs) {
        return active ? Math.max(0, nowMs - startMs) : 0;
    }

    public double averageW() {
        return count == 0 ? 0 : sumW / count;
    }

    public double peakW() {
        return peakW;
    }

    public Summary end(long nowMs, int endLevel) {
        active = false;
        Summary s = new Summary();
        s.endTimeMs = nowMs;
        s.durationMs = Math.max(0, nowMs - startMs);
        s.peakW = peakW;
        s.avgW = averageW();
        s.startLevel = startLevel;
        s.endLevel = endLevel;
        return s;
    }

    public void reset() {
        active = false;
        startMs = 0;
        startLevel = -1;
        sumW = 0;
        count = 0;
        peakW = 0;
    }

    public static final class Summary {
        public long endTimeMs;
        public long durationMs;
        public double peakW;
        public double avgW;
        public int startLevel = -1;
        public int endLevel = -1;
    }
}
