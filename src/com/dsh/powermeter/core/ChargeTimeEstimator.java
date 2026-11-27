package com.dsh.powermeter.core;

/**
 * 预估充满时间。PRD 要求：
 * - 小米机型禁止直接把 computeChargeTimeRemaining() 的返回值当展示值；
 * - 其他品牌对系统 API 做合理性校验，否则回退线性粗估。
 *
 * 本实现不依赖任何系统预估 API，统一使用「当前平滑功率 + 电池容量 + CC/CV 两阶段修正」，
 * 对三家 ROM 口径一致；结果标注为「估算」。
 */
public final class ChargeTimeEstimator {

    private static final double DEFAULT_CAPACITY_AH = 5.0;
    private static final double EFFICIENCY = 0.90;
    private static final long MAX_REASONABLE_SECONDS = 6 * 3600L;

    private ChargeTimeEstimator() {
    }

    /**
     * @param level           当前电量百分比，0–100
     * @param chargeW         平滑后的入电池功率（W）
     * @param packVoltageV    当前整包电压（V）
     * @param chargeCounterUah 系统 charge counter（µAh），未知传 -1
     * @param cells           电芯数
     * @return 预估秒数；无法估算返回 -1
     */
    public static long estimateSeconds(int level, double chargeW, double packVoltageV,
                                       long chargeCounterUah, int cells) {
        if (level < 0 || level >= 100) return -1;
        if (!Double.isFinite(chargeW) || chargeW < 0.5) return -1;
        if (!Double.isFinite(packVoltageV) || packVoltageV < 2.5) {
            packVoltageV = 3.85 * Math.max(1, cells);
        }

        double capacityAh = DEFAULT_CAPACITY_AH;
        if (chargeCounterUah > 0 && level >= 3) {
            double ah = (chargeCounterUah / 1_000_000.0) / (level / 100.0);
            if (ah >= 0.5 && ah <= 25.0) capacityAh = ah;
        }

        double remainWh = (100.0 - level) / 100.0 * capacityAh * packVoltageV;
        double ccvFactor = level >= 90 ? 1.7 : (level >= 80 ? 1.25 : 1.0);
        double seconds = remainWh / (chargeW * EFFICIENCY) * 3600.0 * ccvFactor;
        if (!Double.isFinite(seconds)) return -1;
        if (seconds < 60) seconds = 60;
        if (seconds > MAX_REASONABLE_SECONDS) return -1;
        return (long) seconds;
    }
}
