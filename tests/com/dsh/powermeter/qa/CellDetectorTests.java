package com.dsh.powermeter.qa;

import com.dsh.powermeter.core.CellDetector;
import com.dsh.powermeter.core.RawBattery;

/** 电芯数自动识别：voltage_max、整包电压、已知机型、品牌默认。 */
final class CellDetectorTests {

    private CellDetectorTests() {
    }

    static void run() {
        voltageMaxDual();
        voltageMaxSingle();
        knownDualOverridesLowMax();
        packVoltage();
        brandFallback();
    }

    private static void voltageMaxDual() {
        CellDetector.Result r = CellDetector.detect(
                RawBattery.builder().voltageMv(3950).sysfsVoltageMaxV(8.72).build(),
                false, 1);
        TestKit.eq("voltage_max 8.72V → 2 芯", 2, r.cells);
        TestKit.isTrue("voltage_max 8.72V → 高置信度", r.confident);
    }

    private static void voltageMaxSingle() {
        CellDetector.Result r = CellDetector.detect(
                RawBattery.builder().voltageMv(4300).sysfsVoltageMaxV(4.48).build(),
                false, 2);
        TestKit.eq("voltage_max 4.48V → 1 芯", 1, r.cells);
    }

    private static void knownDualOverridesLowMax() {
        CellDetector.Result r = CellDetector.detect(
                RawBattery.builder().voltageMv(3950).sysfsVoltageMaxV(4.48)
                        .likelyDualCell(true).build(),
                true, 2);
        TestKit.eq("已知双芯优先于单芯 voltage_max", 2, r.cells);
    }

    private static void packVoltage() {
        CellDetector.Result r = CellDetector.detect(
                RawBattery.builder().voltageMv(8600).build(), false, 1);
        TestKit.eq("整包 8.6V → 2 芯", 2, r.cells);
    }

    private static void brandFallback() {
        CellDetector.Result dual = CellDetector.detect(
                RawBattery.builder().voltageMv(3950).build(), false, 2);
        TestKit.eq("无特征品牌默认 2 → 2 芯", 2, dual.cells);
        TestKit.isFalse("品牌默认 → 低置信度", dual.confident);
        CellDetector.Result single = CellDetector.detect(
                RawBattery.builder().voltageMv(4300).build(), false, 1);
        TestKit.eq("无特征品牌默认 1 → 1 芯", 1, single.cells);
    }
}
