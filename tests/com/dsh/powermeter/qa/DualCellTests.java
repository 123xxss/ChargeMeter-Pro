package com.dsh.powermeter.qa;

import com.dsh.powermeter.core.ChargeNormalizer;
import com.dsh.powermeter.core.NormalizedReading;
import com.dsh.powermeter.core.RawBattery;

/**
 * PRD Bug 2：双电芯误判。67W 双电芯机型只上报单电芯电压时功率少一半；
 * 而 18W 单电芯机型若被无条件 ×2 则功率翻倍。
 */
final class DualCellTests {

    private static final int AUTO = ChargeNormalizer.DUAL_AUTO;
    private static final int FORCE = ChargeNormalizer.DUAL_FORCE;
    private static final int OFF = ChargeNormalizer.DUAL_OFF;

    private DualCellTests() {
    }

    static void run() {
        dualCell67W();
        brandHighCurrentWithoutPowerMax();
        singleCell18WMustNotDouble();
        dualOffDisablesCorrection();
        dualForceDoubles();
        packVoltageAlreadyReported();
        powerMax50BoundaryDoubles();
        powerMax49NonBrandStaysSingle();
        enhancedSysfsCellVoltageWith67W();
    }

    /** PRD 指定用例：POWER_MAX=67、EXTRA_VOLTAGE=4300mV、电流 7.7A → 约 66W。 */
    private static void dualCell67W() {
        ChargeNormalizer n = new ChargeNormalizer(ChargeNormalizer.UNIT_UNKNOWN);
        RawBattery raw = Fixtures.charging(7_700_000L).voltageMv(4300).powerMaxW(67).build();
        NormalizedReading r = n.normalize(raw, AUTO, true);

        TestKit.eqDouble("67W 双电芯 → 整包电压 8.6V", 8.6, r.voltageV, 1e-6);
        TestKit.eq("67W 双电芯 → cells=2", 2, r.cells);
        TestKit.isTrue("67W 双电芯 → 标记 voltageCorrected", r.voltageCorrected);
        TestKit.eqDouble("67W 双电芯 → currentA=7.7", 7.7, r.currentA, 1e-6);
        TestKit.range("67W 双电芯 → chargeW 约 66W（62~70）", 62.0, 70.0, r.chargeW);
        TestKit.eqDouble("67W 双电芯 → chargeW=8.6×7.7=66.22", 66.22, r.chargeW, 0.02);
        TestKit.isTrue("67W 双电芯 → valid=true", r.valid);
    }

    /** POWER_MAX=0（未拿到充电器信息）+ 双电芯品牌 + 高电流 → 自动按 2S 修正。 */
    private static void brandHighCurrentWithoutPowerMax() {
        ChargeNormalizer n = new ChargeNormalizer(ChargeNormalizer.UNIT_UNKNOWN);
        RawBattery raw = Fixtures.charging(6_000_000L).voltageMv(4400).powerMaxW(0).build();
        NormalizedReading r = n.normalize(raw, AUTO, true);

        TestKit.eq("品牌机 POWER_MAX=0 高电流 → cells=2", 2, r.cells);
        TestKit.isTrue("品牌机 POWER_MAX=0 高电流 → 标记电压修正", r.voltageCorrected);
        TestKit.eqDouble("品牌机 POWER_MAX=0 高电流 → 整包 8.8V", 8.8, r.voltageV, 1e-6);
        TestKit.eqDouble("品牌机 POWER_MAX=0 高电流 → chargeW=52.8", 52.8, r.chargeW, 1e-6);
    }

    /**
     * 参照 ChargeMeter 的品牌默认口径：vivo/OPPO/小米双电芯品牌默认按 2S 计算，
     * 低功率时也不要求电流 >=5A；单电芯异常机型由用户关闭“双电芯修正”兜底。
     * 非双电芯品牌仍保持单电芯，避免无依据翻倍。
     */
    private static void singleCell18WMustNotDouble() {
        ChargeNormalizer n = new ChargeNormalizer(ChargeNormalizer.UNIT_UNKNOWN);
        RawBattery raw = Fixtures.charging(4_200_000L).voltageMv(4300).powerMaxW(18).build();
        NormalizedReading r = n.normalize(raw, AUTO, true);

        TestKit.eq("双电芯品牌默认 → cells=2", 2, r.cells);
        TestKit.isTrue("双电芯品牌默认 → 标记电压修正", r.voltageCorrected);
        TestKit.eqDouble("双电芯品牌默认 → 整包电压 8.6V", 8.6, r.voltageV, 1e-6);
        TestKit.eqDouble("双电芯品牌默认 → chargeW=36.12", 36.12, r.chargeW, 0.02);
        TestKit.isTrue("双电芯品牌默认 → valid=true", r.valid);

        // 非双电芯品牌、同样 18W：必须保持单电芯，不翻倍。
        ChargeNormalizer n2 = new ChargeNormalizer(ChargeNormalizer.UNIT_UNKNOWN);
        NormalizedReading r2 = n2.normalize(
                Fixtures.charging(4_200_000L).voltageMv(4300).powerMaxW(18).build(), AUTO, false);
        TestKit.eq("18W 单电芯（非品牌）→ cells=1", 1, r2.cells);
        TestKit.range("18W 单电芯（非品牌）→ chargeW 落在 16~20W", 16.0, 20.0, r2.chargeW);
    }

    /** DUAL_OFF：用户关闭双电芯修正后，不得乘 2。 */
    private static void dualOffDisablesCorrection() {
        ChargeNormalizer n = new ChargeNormalizer(ChargeNormalizer.UNIT_UNKNOWN);
        RawBattery raw = Fixtures.charging(7_700_000L).voltageMv(4300).powerMaxW(67).build();
        NormalizedReading r = n.normalize(raw, OFF, true);

        TestKit.eq("DUAL_OFF + 67W → cells=1（修正被关闭）", 1, r.cells);
        TestKit.isFalse("DUAL_OFF → 不标记电压修正", r.voltageCorrected);
        TestKit.eqDouble("DUAL_OFF → 电压保持 4.3V", 4.3, r.voltageV, 1e-6);
        TestKit.eqDouble("DUAL_OFF → chargeW=33.11（不乘 2）", 33.11, r.chargeW, 1e-6);
        TestKit.lt("DUAL_OFF → chargeW 明显低于 AUTO 的 66W", r.chargeW, 40.0);
    }

    /** DUAL_FORCE：强制按 2S 解释（用户手动指定双电芯）。 */
    private static void dualForceDoubles() {
        ChargeNormalizer n = new ChargeNormalizer(ChargeNormalizer.UNIT_UNKNOWN);
        RawBattery raw = Fixtures.charging(4_200_000L).voltageMv(4300).powerMaxW(18).build();
        NormalizedReading r = n.normalize(raw, FORCE, false);

        TestKit.eq("DUAL_FORCE → cells=2", 2, r.cells);
        TestKit.isTrue("DUAL_FORCE → 标记电压修正", r.voltageCorrected);
        TestKit.eqDouble("DUAL_FORCE → 整包 8.6V", 8.6, r.voltageV, 1e-6);
        TestKit.eqDouble("DUAL_FORCE → chargeW=36.12", 36.12, r.chargeW, 1e-6);
    }

    /** 系统已上报整包电压（8600mV）→ 不得再乘一次 2。 */
    private static void packVoltageAlreadyReported() {
        ChargeNormalizer n = new ChargeNormalizer(ChargeNormalizer.UNIT_UNKNOWN);
        RawBattery raw = Fixtures.charging(3_000_000L).voltageMv(8600).powerMaxW(67).build();
        NormalizedReading r = n.normalize(raw, AUTO, true);

        TestKit.eqDouble("整包电压 8.6V 已上报 → 不再翻倍（仍 8.6V）", 8.6, r.voltageV, 1e-6);
        TestKit.isFalse("整包电压已上报 → 不标记电压修正", r.voltageCorrected);
        TestKit.eq("整包电压 8.6V → cells=2", 2, r.cells);
        TestKit.eqDouble("整包电压 8.6V × 3A → chargeW=25.8（不是 51.6）",
                25.8, r.chargeW, 1e-6);
    }

    /** POWER_MAX=50 是 PRD 的双电芯判定下限，边界必须包含。 */
    private static void powerMax50BoundaryDoubles() {
        ChargeNormalizer n = new ChargeNormalizer(ChargeNormalizer.UNIT_UNKNOWN);
        RawBattery raw = Fixtures.charging(5_500_000L).voltageMv(4400).powerMaxW(50).build();
        NormalizedReading r = n.normalize(raw, AUTO, false);

        TestKit.eq("POWER_MAX=50 → cells=2（>=50 判定）", 2, r.cells);
        TestKit.isTrue("POWER_MAX=50 → 标记电压修正", r.voltageCorrected);
        TestKit.eqDouble("POWER_MAX=50 → chargeW=48.4", 48.4, r.chargeW, 1e-6);
    }

    /** POWER_MAX=49 且非双电芯品牌 → 保持单电芯（避免 18~49W 机型被翻倍）。 */
    private static void powerMax49NonBrandStaysSingle() {
        ChargeNormalizer n = new ChargeNormalizer(ChargeNormalizer.UNIT_UNKNOWN);
        RawBattery raw = Fixtures.charging(5_500_000L).voltageMv(4400).powerMaxW(49).build();
        NormalizedReading r = n.normalize(raw, AUTO, false);

        TestKit.eq("POWER_MAX=49 非品牌 → cells=1", 1, r.cells);
        TestKit.isFalse("POWER_MAX=49 非品牌 → 不标记电压修正", r.voltageCorrected);
        TestKit.eqDouble("POWER_MAX=49 非品牌 → chargeW=24.2", 24.2, r.chargeW, 1e-6);
    }

    /** 增强路径（sysfs）给的是单电芯电压 + POWER_MAX=67 → 同样按 2S 修正。 */
    private static void enhancedSysfsCellVoltageWith67W() {
        ChargeNormalizer n = new ChargeNormalizer(ChargeNormalizer.UNIT_UNKNOWN);
        RawBattery raw = RawBattery.builder()
                .timestampMs(0L)
                .status(Fixtures.STATUS_CHARGING)
                .plugged(Fixtures.PLUGGED_AC)
                .level(50)
                .voltageMv(0)
                .temperatureDeciC(300)
                .currentSupported(false)
                .powerMaxW(67)
                .sysfsVoltageV(4.3)
                .sysfsCurrentA(7.7)
                .build();
        NormalizedReading r = n.normalize(raw, AUTO, true);

        TestKit.isTrue("sysfs 增强路径 → enhanced=true", r.enhanced);
        TestKit.eq("sysfs 单电芯 4.3V + 67W → cells=2", 2, r.cells);
        TestKit.isTrue("sysfs 单电芯 4.3V + 67W → 标记电压修正", r.voltageCorrected);
        TestKit.eqDouble("sysfs 增强路径 → 整包 8.6V", 8.6, r.voltageV, 1e-6);
        TestKit.eqDouble("sysfs 增强路径 → chargeW=66.22", 66.22, r.chargeW, 0.02);
        TestKit.isTrue("sysfs 增强路径 → valid=true", r.valid);
    }
}
