package com.dsh.powermeter.core;

/**
 * 平滑峰值：对最近 5 个（约 5 秒）展示功率做滑动平均，取历史最大值。
 * 与 PRD 的「3–5 秒滑动平均后的峰值，并标注平滑峰值」一致。
 */
public final class PeakTracker {

    private final java.util.ArrayDeque<Double> window = new java.util.ArrayDeque<>();
    private double peak;

    public void add(double smoothedWatts) {
        if (!Double.isFinite(smoothedWatts) || smoothedWatts < 0) return;
        window.addLast(smoothedWatts);
        while (window.size() > 5) window.removeFirst();
        double avg = 0;
        for (double v : window) avg += v;
        avg /= window.size();
        if (avg > peak) peak = avg;
    }

    public double peak() {
        return peak;
    }

    public void reset() {
        window.clear();
        peak = 0;
    }
}
