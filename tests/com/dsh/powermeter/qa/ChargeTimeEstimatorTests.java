package com.dsh.powermeter.qa;

import com.dsh.powermeter.core.ChargeTimeEstimator;

/**
 * PRD 要求：充满预估落在合理区间（1 分钟 ~ 6 小时），低功率 / 已满电返回 -1；
 * 不把系统 computeChargeTimeRemaining() 直接当展示值（这里根本不再调用它）。
 */
final class ChargeTimeEstimatorTests {

    private ChargeTimeEstimatorTests() {
    }

    static void run() {
        normalCase();
        reasonableRangeMatrix();
        lowPowerReturnsUnknown();
        fullOrInvalidLevelReturnsUnknown();
        beyondSixHoursReturnsUnknown();
        higherPowerMeansShorter();
        unknownVoltageFallsBack();
        capacityFromChargeCounter();
        badChargeCounterIgnored();
        levelBelowThreeIgnoresCounter();
        minimumOneMinute();
    }

    /** 50% / 30W / 8.6V / 默认 5Ah → 2866 秒（约 48 分钟）。 */
    private static void normalCase() {
        long s = ChargeTimeEstimator.estimateSeconds(50, 30.0, 8.6, -1L, 2);
        TestKit.eqDouble("50% + 30W + 8.6V → 约 2866 秒", 2866, s, 30);
        TestKit.range("50% + 30W → 落在 1 分钟 ~ 6 小时", 60, 6 * 3600, s);
    }

    /** 典型区间矩阵：全部必须给出 1 分钟 ~ 6 小时内的值。 */
    private static void reasonableRangeMatrix() {
        int[][] cases = {
                {0, 50},    // 刚起步满功率
                {20, 45},
                {50, 30},
                {80, 20},   // CV 阶段
                {95, 10},   // 涓流
                {99, 2},
        };
        for (int[] c : cases) {
            int level = c[0];
            int w = c[1];
            long s = ChargeTimeEstimator.estimateSeconds(level, w, 8.6, -1L, 2);
            TestKit.range("预估合理区间 " + level + "% / " + w + "W", 60, 6 * 3600, s);
        }
    }

    /** 功率 < 0.5W → 无法估算。 */
    private static void lowPowerReturnsUnknown() {
        TestKit.eq("低功率 0.1W → -1", -1, ChargeTimeEstimator.estimateSeconds(50, 0.1, 8.6, -1L, 2));
        TestKit.eq("低功率 0.49W → -1", -1, ChargeTimeEstimator.estimateSeconds(50, 0.49, 8.6, -1L, 2));
        TestKit.eq("功率 NaN → -1", -1,
                ChargeTimeEstimator.estimateSeconds(50, Double.NaN, 8.6, -1L, 2));
    }

    /** 已满电 / 非法电量 → 无法估算。 */
    private static void fullOrInvalidLevelReturnsUnknown() {
        TestKit.eq("电量 100% → -1", -1, ChargeTimeEstimator.estimateSeconds(100, 30.0, 8.6, -1L, 2));
        TestKit.eq("电量 -1 → -1", -1, ChargeTimeEstimator.estimateSeconds(-1, 30.0, 8.6, -1L, 2));
    }

    /** 预估超过 6 小时 → 认为不可信，返回 -1。 */
    private static void beyondSixHoursReturnsUnknown() {
        TestKit.eq("10% + 0.5W → 超过 6 小时 → -1", -1,
                ChargeTimeEstimator.estimateSeconds(10, 0.5, 8.6, -1L, 2));
        TestKit.eq("50% + 3W → 超过 6 小时 → -1", -1,
                ChargeTimeEstimator.estimateSeconds(50, 3.0, 8.6, -1L, 2));
    }

    /** 功率越高预估越短（同一电量下）。 */
    private static void higherPowerMeansShorter() {
        long low = ChargeTimeEstimator.estimateSeconds(50, 10.0, 8.6, -1L, 2);
        long mid = ChargeTimeEstimator.estimateSeconds(50, 30.0, 8.6, -1L, 2);
        long high = ChargeTimeEstimator.estimateSeconds(50, 60.0, 8.6, -1L, 2);
        TestKit.gt("50% + 10W 比 +30W 更慢", low, mid);
        TestKit.gt("50% + 30W 比 +60W 更慢", mid, high);
        TestKit.eqDouble("50% + 60W → 约 1433 秒", 1433, high, 30);
        TestKit.eqDouble("50% + 10W → 8600 秒", 8600, low, 30);
    }

    /** 电压未知（NaN 或 <2.5V）→ 回退 3.85×cells，不能除零或给 -1。 */
    private static void unknownVoltageFallsBack() {
        long nanPack = ChargeTimeEstimator.estimateSeconds(50, 30.0, Double.NaN, -1L, 2);
        TestKit.eqDouble("电压 NaN → 回退 3.85×2 电芯，约 2566 秒", 2566, nanPack, 30);
        long tinyPack = ChargeTimeEstimator.estimateSeconds(50, 30.0, 1.0, -1L, 2);
        TestKit.eqDouble("电压 1.0V 不可信 → 同样回退，约 2566 秒", 2566, tinyPack, 30);
        long cellFallback = ChargeTimeEstimator.estimateSeconds(50, 30.0, 1.0, -1L, 1);
        TestKit.eqDouble("单电芯电压回退 3.85V → 约 1283 秒", 1283, cellFallback, 30);
    }

    /** 已知 charge counter 时按实际容量估算：2Ah@50% → 4Ah，比默认 5Ah 更快。 */
    private static void capacityFromChargeCounter() {
        long withCounter = ChargeTimeEstimator.estimateSeconds(50, 30.0, 8.6, 2_000_000L, 2);
        long defaultCap = ChargeTimeEstimator.estimateSeconds(50, 30.0, 8.6, -1L, 2);
        TestKit.eqDouble("charge counter=2Ah@50% → 推算 4Ah，约 2293 秒",
                2293, withCounter, 30);
        TestKit.lt("使用实际容量比默认 5Ah 预估更快", withCounter, defaultCap);
    }

    /** 离谱的 charge counter（推算容量 <0.5Ah）→ 忽略，回落默认容量。 */
    private static void badChargeCounterIgnored() {
        long s = ChargeTimeEstimator.estimateSeconds(50, 30.0, 8.6, 100_000L, 2);
        TestKit.eqDouble("离谱 charge counter → 回落默认容量，约 2866 秒", 2866, s, 30);
    }

    /** 电量 <3% 不用 counter 推算（此时电流还不稳）。 */
    private static void levelBelowThreeIgnoresCounter() {
        long s = ChargeTimeEstimator.estimateSeconds(2, 30.0, 8.6, 2_000_000L, 2);
        TestKit.eqDouble("2% 时忽略 charge counter → 按 5Ah 约 5618 秒", 5618, s, 30);
        TestKit.range("2% 预估仍在 1 分钟 ~ 6 小时", 60, 6 * 3600, s);
    }

    /** 极短预估落到下限 60 秒，而不是 0。 */
    private static void minimumOneMinute() {
        long s = ChargeTimeEstimator.estimateSeconds(99, 100.0, 8.6, -1L, 2);
        TestKit.eq("99% + 100W → 下限 60 秒", 60, s);
    }
}
