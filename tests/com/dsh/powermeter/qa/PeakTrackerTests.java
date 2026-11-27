package com.dsh.powermeter.qa;

import com.dsh.powermeter.core.PeakTracker;
import com.dsh.powermeter.core.PowerSmoother;

/**
 * PRD 要求：峰值取「3–5 秒平滑功率」的最大值，并标注为平滑峰值；
 * 单点采样尖峰（插拔瞬间、单帧异常）不得被当作峰值展示。
 */
final class PeakTrackerTests {

    private PeakTrackerTests() {
    }

    static void run() {
        steadyPeak();
        pipelineRejectsSingleSpike();
        pipelineRejectsDoubleSpike();
        unSmoothedSingleSpikeIsAveraged();
        peakDoesNotDecay();
        invalidInputIgnored();
        resetClearsPeak();
    }

    /** 稳态 20W × 5 → 峰值 20W。 */
    private static void steadyPeak() {
        PeakTracker p = new PeakTracker();
        for (int i = 0; i < 5; i++) p.add(20.0);
        TestKit.eqDouble("稳态 20W × 5 → 峰值 20W", 20.0, p.peak(), 1e-9);
    }

    /** 平滑器 + 峰值器串联：单点 200W 尖峰不抬高平滑峰值。 */
    private static void pipelineRejectsSingleSpike() {
        PowerSmoother smoother = new PowerSmoother();
        PeakTracker peak = new PeakTracker();
        for (int i = 0; i < 5; i++) peak.add(smoother.addPower(45.0));
        TestKit.eqDouble("串联：稳态 45W → 平滑峰值 45W", 45.0, peak.peak(), 1e-9);

        peak.add(smoother.addPower(200.0));
        TestKit.lt("串联：单点 200W 尖峰 → 平滑峰值不跳变（< 46）", peak.peak(), 46.0);
        TestKit.eqDouble("串联：单点尖峰后平滑峰值仍为 45", 45.0, peak.peak(), 1e-9);
    }

    /** 连续两点尖峰也不抬高平滑峰值。 */
    private static void pipelineRejectsDoubleSpike() {
        PowerSmoother smoother = new PowerSmoother();
        PeakTracker peak = new PeakTracker();
        for (int i = 0; i < 5; i++) peak.add(smoother.addPower(45.0));

        peak.add(smoother.addPower(200.0));
        peak.add(smoother.addPower(200.0));
        TestKit.lt("串联：连续 2 点 200W → 平滑峰值仍不抬高（< 46）", peak.peak(), 46.0);
    }

    /** 即使直接把未平滑的尖峰喂给 PeakTracker，5 点均值也应明显削弱单点尖峰。 */
    private static void unSmoothedSingleSpikeIsAveraged() {
        PeakTracker p = new PeakTracker();
        for (int i = 0; i < 4; i++) p.add(10.0);
        p.add(100.0);
        TestKit.lt("未平滑单点 100W → 平滑峰值远低于 100", p.peak(), 50.0);
        TestKit.gt("未平滑单点 100W → 平滑峰值仍高于稳态 10（未被清零）", p.peak(), 10.0);
        TestKit.eqDouble("未平滑单点 → 5 点均值 = (10×4+100)/5 = 28",
                28.0, p.peak(), 1e-9);
    }

    /** 峰值只增不减：之后功率下降，历史峰值保留。 */
    private static void peakDoesNotDecay() {
        PeakTracker p = new PeakTracker();
        for (int i = 0; i < 5; i++) p.add(45.0);
        double before = p.peak();
        for (int i = 0; i < 10; i++) p.add(5.0);
        TestKit.eqDouble("功率回落后峰值保留 45", before, p.peak(), 1e-9);
        TestKit.eqDouble("功率回落后峰值仍为 45", 45.0, p.peak(), 1e-9);
    }

    /** NaN / 负数不参与峰值计算。 */
    private static void invalidInputIgnored() {
        PeakTracker p = new PeakTracker();
        p.add(30.0);
        p.add(Double.NaN);
        p.add(-10.0);
        TestKit.eqDouble("NaN / 负数输入不改变峰值", 30.0, p.peak(), 1e-9);
    }

    /** reset 后峰值归零。 */
    private static void resetClearsPeak() {
        PeakTracker p = new PeakTracker();
        for (int i = 0; i < 5; i++) p.add(45.0);
        p.reset();
        TestKit.eqDouble("reset 后峰值=0", 0.0, p.peak(), 1e-9);
        p.add(30.0);
        TestKit.eqDouble("reset 后重新计峰 → 30（不残留 45）", 30.0, p.peak(), 1e-9);
    }
}
