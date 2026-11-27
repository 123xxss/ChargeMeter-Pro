package com.dsh.powermeter.qa;

import com.dsh.powermeter.core.SessionTracker;

/**
 * PRD 要求：本次充电会话的时长、均功率、峰值、起止电量摘要正确；
 * 极小的功率噪声不得开启会话。
 */
final class SessionTrackerTests {

    private SessionTrackerTests() {
    }

    static void run() {
        sessionLifecycle();
        summaryFields();
        secondSessionIndependent();
        tinyPowerDoesNotStart();
        endWithoutStart();
        resetClearsSession();
    }

    /** 开始 / 结束 / 时长 / 均值 / 峰值。 */
    private static void sessionLifecycle() {
        SessionTracker t = new SessionTracker();
        TestKit.isFalse("初始未激活", t.active());
        TestKit.isFalse("首个充电采样不返回「刚结束」", t.update(true, 45.0, 30, 1000L));
        TestKit.isTrue("首个充电采样后激活", t.active());

        t.update(true, 30.0, 35, 2000L);
        t.update(true, 15.0, 40, 3000L);

        TestKit.eqDouble("均功率 (45+30+15)/3=30", 30.0, t.averageW(), 1e-9);
        TestKit.eqDouble("峰值 45", 45.0, t.peakW(), 1e-9);
        TestKit.eq("时长 3000ms", 3000, t.durationMs(4000L));

        TestKit.isTrue("停止充电采样 → 返回「刚结束」", t.update(false, 0.0, 40, 3500L));
        TestKit.isFalse("结束后未激活", t.active());
    }

    /** Summary 字段完整且数值正确。 */
    private static void summaryFields() {
        SessionTracker t = new SessionTracker();
        t.update(true, 45.0, 30, 1000L);
        t.update(true, 30.0, 35, 2000L);
        t.update(true, 15.0, 40, 3000L);
        t.update(false, 0.0, 40, 3500L);

        SessionTracker.Summary s = t.end(4000L, 55);
        TestKit.eq("摘要结束时间 4000", 4000, s.endTimeMs);
        TestKit.eq("摘要时长 3000ms", 3000, s.durationMs);
        TestKit.eqDouble("摘要峰值 45", 45.0, s.peakW, 1e-9);
        TestKit.eqDouble("摘要均功率 30", 30.0, s.avgW, 1e-9);
        TestKit.eq("摘要起始电量 30%", 30, s.startLevel);
        TestKit.eq("摘要结束电量 55%", 55, s.endLevel);
    }

    /** 第二次会话使用独立统计，不继承上一次的均值/峰值/起始电量。 */
    private static void secondSessionIndependent() {
        SessionTracker tracker = new SessionTracker();
        tracker.update(true, 45.0, 30, 1000L);
        tracker.update(false, 0.0, 40, 2000L);
        SessionTracker.Summary first = tracker.end(2500L, 40);
        TestKit.eqDouble("第一段会话峰值 45", 45.0, first.peakW, 1e-9);

        TestKit.isFalse("第二次充电采样不返回「刚结束」", tracker.update(true, 20.0, 60, 10000L));
        TestKit.isTrue("第二次会话激活", tracker.active());
        TestKit.eqDouble("第二次会话均值只有 20（不残留 45）", 20.0, tracker.averageW(), 1e-9);
        TestKit.eqDouble("第二次会话峰值只有 20", 20.0, tracker.peakW(), 1e-9);

        SessionTracker.Summary second = tracker.end(11000L, 61);
        TestKit.eq("第二次会话时长 1000ms", 1000, second.durationMs);
        TestKit.eq("第二次会话起始电量 60%", 60, second.startLevel);
        TestKit.eqDouble("第二次会话均功率 20", 20.0, second.avgW, 1e-9);
        TestKit.eqDouble("第一次摘要不受第二次影响", 45.0, first.peakW, 1e-9);
    }

    /** 0.05W 及以下的噪声不得开启会话。 */
    private static void tinyPowerDoesNotStart() {
        SessionTracker t = new SessionTracker();
        TestKit.isFalse("0.04W 噪声 → 不开启会话", t.update(true, 0.04, 50, 1000L));
        TestKit.isFalse("0.04W 噪声 → 仍未激活", t.active());
        TestKit.isFalse("0.05W 边界 → 不开启会话", t.update(true, 0.05, 50, 2000L));
        TestKit.isFalse("0W → 不开启会话", t.update(true, 0.0, 50, 3000L));
        TestKit.isFalse("已有会话时未充电 → 不重复返回结束", t.update(false, 0.0, 50, 4000L));
    }

    /** 未开始就结束 → 不返回「刚结束」。 */
    private static void endWithoutStart() {
        SessionTracker t = new SessionTracker();
        TestKit.isFalse("未开始会话时放电采样 → 不返回结束", t.update(false, 0.0, 10, 100L));
    }

    /** reset 清空当前会话统计。 */
    private static void resetClearsSession() {
        SessionTracker t = new SessionTracker();
        t.update(true, 45.0, 30, 1000L);
        t.reset();
        TestKit.isFalse("reset 后未激活", t.active());
        TestKit.eqDouble("reset 后均功率 0", 0.0, t.averageW(), 1e-9);
        TestKit.eqDouble("reset 后峰值 0", 0.0, t.peakW(), 1e-9);
    }
}
