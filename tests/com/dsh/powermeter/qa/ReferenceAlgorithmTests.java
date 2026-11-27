package com.dsh.powermeter.qa;

import com.dsh.powermeter.core.ChargeNormalizer;
import com.dsh.powermeter.core.NormalizedReading;
import com.dsh.powermeter.core.RawBattery;

/** ChargeMeter 兼容功率算法：P = |I| × V × 电芯数 × 校准倍率（倍率由上层应用）。 */
final class ReferenceAlgorithmTests {

    private ReferenceAlgorithmTests() {
    }

    static void run() {
        matchesUserScreenshot();
        singleCell();
        packVoltage();
        milliAmpUnit();
        singleCellPackVoltageInvalid();
        currentUnsupported();
        shizukuFallback();
    }

    private static void matchesUserScreenshot() {
        ChargeNormalizer n = new ChargeNormalizer(0);
        NormalizedReading r = n.normalize(Fixtures.charging(1_880_000L).voltageMv(3950).build(),
                ChargeNormalizer.DUAL_AUTO, true, 2);
        TestKit.isTrue("参考算法 3.95V/1.88A/2芯 → valid", r.valid);
        TestKit.eq("参考算法 → cells=2", 2, r.cells);
        TestKit.eqDouble("参考算法 → 整包电压 7.90V", 7.90, r.voltageV, 1e-6);
        TestKit.eqDouble("参考算法 → 功率 14.85W", 14.85, r.chargeW, 0.01);
    }

    private static void singleCell() {
        ChargeNormalizer n = new ChargeNormalizer(0);
        NormalizedReading r = n.normalize(Fixtures.charging(4_200_000L).voltageMv(4300).build(),
                ChargeNormalizer.DUAL_AUTO, true, 1);
        TestKit.eq("参考算法单电芯 → cells=1", 1, r.cells);
        TestKit.eqDouble("参考算法单电芯 → 4.3×4.2=18.06W", 18.06, r.chargeW, 0.02);
    }

    private static void packVoltage() {
        ChargeNormalizer n = new ChargeNormalizer(0);
        NormalizedReading r = n.normalize(Fixtures.charging(3_000_000L).voltageMv(8600).build(),
                ChargeNormalizer.DUAL_AUTO, true, 2);
        TestKit.isTrue("部分品牌整包电压 8.6V/2 芯 → 按整包计算", r.valid);
        TestKit.eqDouble("整包 8.6V × 3.0A = 25.8W", 25.8, r.chargeW, 0.02);
        TestKit.eqDouble("整包电压显示仍为 8.60V", 8.60, r.voltageV, 1e-6);
    }

    private static void milliAmpUnit() {
        ChargeNormalizer n = new ChargeNormalizer(0);
        NormalizedReading r = n.normalize(Fixtures.charging(1_880L).voltageMv(3950).build(),
                ChargeNormalizer.DUAL_AUTO, true, 2);
        TestKit.isTrue("mA 上报 1880 × 3.95V × 2 芯 → valid", r.valid);
        TestKit.eqDouble("mA 上报 → 14.85W", 14.85, r.chargeW, 0.02);
    }

    private static void singleCellPackVoltageInvalid() {
        ChargeNormalizer n = new ChargeNormalizer(0);
        NormalizedReading r = n.normalize(Fixtures.charging(3_000_000L).voltageMv(8600).build(),
                ChargeNormalizer.DUAL_AUTO, true, 1);
        TestKit.isFalse("单电芯 8.6V 整包 → 不可用", r.valid);
    }

    private static void currentUnsupported() {
        ChargeNormalizer n = new ChargeNormalizer(0);
        RawBattery raw = RawBattery.builder().status(2).plugged(1).level(50)
                .voltageMv(4300).currentRaw(0).currentSupported(false).build();
        NormalizedReading r = n.normalize(raw, ChargeNormalizer.DUAL_AUTO, true, 2);
        TestKit.isFalse("参考算法无电流接口 → 不可用", r.valid);
    }

    private static void shizukuFallback() {
        ChargeNormalizer n = new ChargeNormalizer(0);
        RawBattery raw = RawBattery.builder().status(2).plugged(1).level(50)
                .voltageMv(0).currentSupported(false)
                .sysfsVoltageV(3.95).sysfsCurrentA(1.88).build();
        NormalizedReading r = n.normalize(raw, ChargeNormalizer.DUAL_AUTO, true, 2);
        TestKit.isTrue("Shizuku 兜底 → valid", r.valid);
        TestKit.isTrue("Shizuku 兜底 → enhanced", r.enhanced);
        TestKit.eqDouble("Shizuku 兜底 → 14.85W", 14.85, r.chargeW, 0.01);
    }
}
