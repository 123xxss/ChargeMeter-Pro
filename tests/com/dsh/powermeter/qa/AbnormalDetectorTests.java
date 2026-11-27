package com.dsh.powermeter.qa;

import com.dsh.powermeter.core.AbnormalDetector;

/**
 * PRD 要求：异常提示不能插拔即误报。
 * - 过温（>=45℃）立即提示；
 * - 持续低功率必须满 30 秒才提示，中途恢复则不计；
 * - 20 秒内剧烈跳变多次才提示；
 * - 未充电时清空状态。
 */
final class AbnormalDetectorTests {

    private AbnormalDetectorTests() {
    }

    static void run() {
        overheatImmediate();
        noFalseAlarmOnPlugIn();
        recoveryCancelsLowPowerTimer();
        unstablePowerDetected();
        unplugResetsState();
        overheatWinsOverLowPower();
        nanTemperatureIgnored();
    }

    /** 45℃ 立即提示过温。 */
    private static void overheatImmediate() {
        AbnormalDetector d = new AbnormalDetector();
        TestKit.eq("45.0℃ → 立即 OVERHEAT",
                AbnormalDetector.OVERHEAT, d.update(0L, true, 60.0, 45.0, 0.0));

        AbnormalDetector d2 = new AbnormalDetector();
        TestKit.eq("50.0℃ → 立即 OVERHEAT",
                AbnormalDetector.OVERHEAT, d2.update(0L, true, 20.0, 50.0, 0.0));
    }

    /** 刚插电满功率 → 不误报；功率掉到 1/12 也必须等满 30 秒。 */
    private static void noFalseAlarmOnPlugIn() {
        AbnormalDetector d = new AbnormalDetector();
        TestKit.eq("刚插电 60W → NONE",
                AbnormalDetector.NONE, d.update(0L, true, 60.0, 30.0, 0.0));
        TestKit.eq("插电后 1 秒掉到 5W → 不立刻误报",
                AbnormalDetector.NONE, d.update(1000L, true, 5.0, 30.0, 60.0));
        TestKit.eq("低功率持续 19 秒 → 仍不报",
                AbnormalDetector.NONE, d.update(20000L, true, 5.0, 30.0, 60.0));
        TestKit.eq("低功率持续 29.999 秒 → 仍不报",
                AbnormalDetector.NONE, d.update(30999L, true, 5.0, 30.0, 60.0));
        TestKit.eq("低功率持续满 30 秒 → POWER_DROP",
                AbnormalDetector.POWER_DROP, d.update(31000L, true, 5.0, 30.0, 60.0));
        TestKit.eq("提示保持期内（60 秒）继续提示",
                AbnormalDetector.POWER_DROP, d.update(40000L, true, 5.0, 30.0, 60.0));
        TestKit.eq("提示保持期结束后恢复正常 → NONE",
                AbnormalDetector.NONE, d.update(95000L, true, 50.0, 30.0, 60.0));
    }

    /** 恢复供电后重新计时：必须从新的低功率起点再满 30 秒。 */
    private static void recoveryCancelsLowPowerTimer() {
        AbnormalDetector d = new AbnormalDetector();
        TestKit.eq("低功率开始（1s）→ NONE",
                AbnormalDetector.NONE, d.update(1000L, true, 5.0, 30.0, 60.0));
        TestKit.eq("低功率持续（5s）→ NONE",
                AbnormalDetector.NONE, d.update(5000L, true, 6.0, 30.0, 60.0));
        TestKit.eq("功率恢复到 50W（20s）→ NONE",
                AbnormalDetector.NONE, d.update(20000L, true, 50.0, 30.0, 60.0));
        TestKit.eq("再次掉到 5W（25s）→ 重新计时 NONE",
                AbnormalDetector.NONE, d.update(25000L, true, 5.0, 30.0, 60.0));
        TestKit.eq("重新计时 29.999 秒 → NONE",
                AbnormalDetector.NONE, d.update(54999L, true, 5.0, 30.0, 60.0));
        TestKit.eq("重新计时满 30 秒 → POWER_DROP",
                AbnormalDetector.POWER_DROP, d.update(55000L, true, 5.0, 30.0, 60.0));
    }

    /** 20 秒内 5 次大幅波动 → UNSTABLE；前面几次不应提前报。 */
    private static void unstablePowerDetected() {
        AbnormalDetector d = new AbnormalDetector();
        TestKit.eq("60W 起步 → NONE", AbnormalDetector.NONE,
                d.update(0L, true, 60.0, 30.0, 0.0));
        TestKit.eq("跳变 1 次 → NONE", AbnormalDetector.NONE,
                d.update(1000L, true, 20.0, 30.0, 0.0));
        TestKit.eq("跳变 2 次 → NONE", AbnormalDetector.NONE,
                d.update(2000L, true, 60.0, 30.0, 0.0));
        TestKit.eq("跳变 3 次 → NONE", AbnormalDetector.NONE,
                d.update(3000L, true, 20.0, 30.0, 0.0));
        TestKit.eq("跳变 4 次 → NONE", AbnormalDetector.NONE,
                d.update(4000L, true, 60.0, 30.0, 0.0));
        TestKit.eq("20 秒内第 5 次跳变 → UNSTABLE",
                AbnormalDetector.UNSTABLE, d.update(5000L, true, 20.0, 30.0, 0.0));
    }

    /** 拔电 → 返回 NONE 并清空内部计时。 */
    private static void unplugResetsState() {
        AbnormalDetector d = new AbnormalDetector();
        d.update(0L, true, 60.0, 30.0, 0.0);
        d.update(1000L, true, 5.0, 30.0, 60.0);
        TestKit.eq("拔电（未充电）→ NONE",
                AbnormalDetector.NONE, d.update(2000L, false, 0.0, 50.0, 60.0));
        TestKit.eq("拔电后重新插电满功率 → NONE（不残留低功率计时）",
                AbnormalDetector.NONE, d.update(3000L, true, 60.0, 30.0, 0.0));
    }

    /** 过温优先于低功率判定。 */
    private static void overheatWinsOverLowPower() {
        AbnormalDetector d = new AbnormalDetector();
        TestKit.eq("低功率 + 过温 → OVERHEAT（优先）",
                AbnormalDetector.OVERHEAT, d.update(31000L, true, 5.0, 50.0, 60.0));
    }

    /** 温度未知（NaN）→ 不误报过温。 */
    private static void nanTemperatureIgnored() {
        AbnormalDetector d = new AbnormalDetector();
        TestKit.eq("温度 NaN → 不误报过温",
                AbnormalDetector.NONE, d.update(0L, true, 60.0, Double.NaN, 0.0));
    }
}
