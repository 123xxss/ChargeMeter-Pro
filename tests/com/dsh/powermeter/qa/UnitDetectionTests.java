package com.dsh.powermeter.qa;

import com.dsh.powermeter.core.ChargeNormalizer;
import com.dsh.powermeter.core.NormalizedReading;
import com.dsh.powermeter.core.RawBattery;

/**
 * PRD Bug 3：电流单位识别。Android 文档 CURRENT_NOW 单位是 µA，
 * 但个别 ROM 上报 mA。按 mA 解释会把 5A 显示成 5mA（功率≈0），
 * 按 µA 解释会把 mA 机型的读数放大 1000 倍。识别必须稳定、不能反复横跳。
 */
final class UnitDetectionTests {

    private static final int AUTO = ChargeNormalizer.DUAL_AUTO;

    private UnitDetectionTests() {
    }

    static void run() {
        microAmpsNormal();
        milliDeviceByStreak();
        milliDeviceByStreak8000();
        milliSwitchRequiresFourConsecutive();
        plausibleMicroNeverSwitches();
        milliHintUsedDirectly();
        milliHintFallsBackToMicro();
        enhancedPathNegativeCurrentSign();
    }

    /** 标准 µA 设备：3A 必须就是 3A。 */
    private static void microAmpsNormal() {
        ChargeNormalizer n = new ChargeNormalizer(ChargeNormalizer.UNIT_UNKNOWN);
        NormalizedReading r = n.normalize(
                Fixtures.charging(3_000_000L).voltageMv(4300).build(), AUTO, false);

        TestKit.eqDouble("µA 设备 3,000,000 → currentA=3.0", 3.0, r.currentA, 1e-6);
        TestKit.eq("µA 设备 → unitUsed=MICRO", ChargeNormalizer.UNIT_MICRO, r.unitUsed);
        TestKit.eqDouble("µA 设备 3A @4.3V → chargeW=12.9", 12.9, r.chargeW, 1e-6);
        TestKit.isTrue("µA 设备 → 读数有效", r.valid);
        TestKit.eq("µA 设备 → preferredUnit 保持 MICRO",
                ChargeNormalizer.UNIT_MICRO, n.preferredUnit());
    }

    /**
     * mA 设备（raw=5000 实际是 5A）：前 3 次按 µA 解释会接近 0（预期过渡行为），
     * 连续第 4 次采样切换为 mA，输出 5A / 21.5W。
     */
    private static void milliDeviceByStreak() {
        ChargeNormalizer n = new ChargeNormalizer(ChargeNormalizer.UNIT_UNKNOWN);
        RawBattery raw = Fixtures.charging(5_000L).voltageMv(4300).build();

        NormalizedReading first = n.normalize(raw, AUTO, false);
        TestKit.eq("mA 设备第 1 次采样 → 仍按 µA", ChargeNormalizer.UNIT_MICRO, first.unitUsed);
        TestKit.isTrue("mA 设备第 1 次采样 → 按 µA 解释接近零（nearZero）", first.nearZero);
        TestKit.isFalse("mA 设备第 1 次采样 → 不输出错误功率（valid=false）", first.valid);
        TestKit.eqDouble("mA 设备第 1 次采样 → chargeW=0", 0.0, first.chargeW, 1e-9);

        n.normalize(raw, AUTO, false); // 第 2 次
        n.normalize(raw, AUTO, false); // 第 3 次
        NormalizedReading fourth = n.normalize(raw, AUTO, false); // 第 4 次：切换

        TestKit.eq("mA 设备连续第 4 次采样 → unitUsed=MILLI",
                ChargeNormalizer.UNIT_MILLI, fourth.unitUsed);
        TestKit.eqDouble("mA 设备 raw=5000 → currentA=5.0", 5.0, fourth.currentA, 1e-6);
        TestKit.eqDouble("mA 设备 5A @4.3V → chargeW=21.5", 21.5, fourth.chargeW, 1e-6);
        TestKit.isTrue("mA 设备切换后 → 读数有效", fourth.valid);
        TestKit.eq("mA 设备切换后 → preferredUnit=MILLI",
                ChargeNormalizer.UNIT_MILLI, n.preferredUnit());

        NormalizedReading fifth = n.normalize(raw, AUTO, false);
        TestKit.eq("mA 设备第 5 次采样 → 保持 MILLI",
                ChargeNormalizer.UNIT_MILLI, fifth.unitUsed);
        TestKit.eqDouble("mA 设备第 5 次采样 → 仍为 5.0A", 5.0, fifth.currentA, 1e-6);
    }

    /** raw=8000 → 8A / 34.4W（同一套连续 4 次判定）。 */
    private static void milliDeviceByStreak8000() {
        ChargeNormalizer n = new ChargeNormalizer(ChargeNormalizer.UNIT_UNKNOWN);
        RawBattery raw = Fixtures.charging(8_000L).voltageMv(4300).build();
        n.normalize(raw, AUTO, false);
        n.normalize(raw, AUTO, false);
        n.normalize(raw, AUTO, false);
        NormalizedReading fourth = n.normalize(raw, AUTO, false);

        TestKit.eq("mA 设备 raw=8000 连续第 4 次 → MILLI",
                ChargeNormalizer.UNIT_MILLI, fourth.unitUsed);
        TestKit.eqDouble("mA 设备 raw=8000 → currentA=8.0", 8.0, fourth.currentA, 1e-6);
        TestKit.eqDouble("mA 设备 8A @4.3V → chargeW=34.4", 34.4, fourth.chargeW, 1e-6);
        TestKit.isTrue("mA 设备 raw=8000 切换后 → valid=true", fourth.valid);
    }

    /**
     * 切换必须要求「连续」4 次：中间插入一个明显按 µA 合理的样本会重置计数。
     */
    private static void milliSwitchRequiresFourConsecutive() {
        ChargeNormalizer n = new ChargeNormalizer(ChargeNormalizer.UNIT_UNKNOWN);
        RawBattery milli = Fixtures.charging(5_000L).voltageMv(4300).build();
        RawBattery micro = Fixtures.charging(200_000L).voltageMv(4300).build();

        n.normalize(milli, AUTO, false); // streak=1
        n.normalize(milli, AUTO, false); // streak=2
        n.normalize(milli, AUTO, false); // streak=3
        NormalizedReading reset = n.normalize(micro, AUTO, false); // 重置 streak
        TestKit.eq("中间插入合理 µA 样本 → 重置连续计数", ChargeNormalizer.UNIT_MICRO, reset.unitUsed);
        TestKit.eqDouble("重置样本 0.2A @4.3V → chargeW=0.86", 0.86, reset.chargeW, 1e-6);

        n.normalize(milli, AUTO, false); // streak=1
        n.normalize(milli, AUTO, false); // streak=2
        NormalizedReading thirdAfterReset = n.normalize(milli, AUTO, false); // streak=3
        TestKit.eq("重置后仅连续 3 次 → 仍不切换",
                ChargeNormalizer.UNIT_MICRO, thirdAfterReset.unitUsed);
        TestKit.eqDouble("重置后第 3 次 → chargeW=0（未给错误功率）",
                0.0, thirdAfterReset.chargeW, 1e-9);

        NormalizedReading fourthAfterReset = n.normalize(milli, AUTO, false); // streak=4
        TestKit.eq("重置后连续第 4 次 → 切换 MILLI",
                ChargeNormalizer.UNIT_MILLI, fourthAfterReset.unitUsed);
        TestKit.eqDouble("重置后切换 → currentA=5.0", 5.0, fourthAfterReset.currentA, 1e-6);
    }

    /** 明显合理的 µA 读数（3A）连续多次不得被误判成 mA。 */
    private static void plausibleMicroNeverSwitches() {
        ChargeNormalizer n = new ChargeNormalizer(ChargeNormalizer.UNIT_UNKNOWN);
        RawBattery raw = Fixtures.charging(3_000_000L).voltageMv(4300).build();
        NormalizedReading last = null;
        for (int i = 0; i < 6; i++) {
            last = n.normalize(raw, AUTO, false);
        }
        TestKit.notNull("µA 正常设备连续 6 次采样 → 有结果", last);
        TestKit.eq("µA 正常设备连续 6 次 → 不误切换",
                ChargeNormalizer.UNIT_MICRO, last.unitUsed);
        TestKit.eqDouble("µA 正常设备连续 6 次 → 仍 3.0A", 3.0, last.currentA, 1e-6);
        TestKit.eq("µA 正常设备 → preferredUnit 仍 MICRO",
                ChargeNormalizer.UNIT_MICRO, n.preferredUnit());
    }

    /** 装机时已知是 mA 设备（unitHint=MILLI）→ 直接按 mA 解释。 */
    private static void milliHintUsedDirectly() {
        ChargeNormalizer n = new ChargeNormalizer(ChargeNormalizer.UNIT_MILLI);
        NormalizedReading r = n.normalize(
                Fixtures.charging(5_000L).voltageMv(4300).build(), AUTO, false);

        TestKit.eq("mA 提示 → unitUsed=MILLI", ChargeNormalizer.UNIT_MILLI, r.unitUsed);
        TestKit.eqDouble("mA 提示 raw=5000 → currentA=5.0", 5.0, r.currentA, 1e-6);
        TestKit.eqDouble("mA 提示 raw=5000 @4.3V → chargeW=21.5", 21.5, r.chargeW, 1e-6);
        TestKit.isTrue("mA 提示 → 首次采样即有效", r.valid);
    }

    /** mA 提示但数值按 mA 不合理（300000 会变 300A）→ 回退 µA 并要求合理。 */
    private static void milliHintFallsBackToMicro() {
        ChargeNormalizer n = new ChargeNormalizer(ChargeNormalizer.UNIT_MILLI);
        NormalizedReading r = n.normalize(
                Fixtures.charging(300_000L).voltageMv(4300).build(), AUTO, false);

        TestKit.eq("mA 提示但 mA 解释不合理 → 回退 MICRO",
                ChargeNormalizer.UNIT_MICRO, r.unitUsed);
        TestKit.eqDouble("回退 MICRO 后 currentA=0.3", 0.3, r.currentA, 1e-6);
        TestKit.eq("回退后 preferredUnit=MICRO",
                ChargeNormalizer.UNIT_MICRO, n.preferredUnit());
    }

    /** 增强路径（sysfs 电流）也必须做符号归一：负电流 → 正充电功率。 */
    private static void enhancedPathNegativeCurrentSign() {
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
                .sysfsVoltageV(8.6)
                .sysfsCurrentA(-7.7)
                .build();
        NormalizedReading r = n.normalize(raw, AUTO, true);

        TestKit.isTrue("sysfs 增强路径 → enhanced=true", r.enhanced);
        TestKit.eq("sysfs 8.6V 整包 → cells=2", 2, r.cells);
        TestKit.eqDouble("sysfs 负电流 -7.7A → currentA=+7.7", 7.7, r.currentA, 1e-6);
        TestKit.eqDouble("sysfs 负电流 → chargeW=66.22（正功率）", 66.22, r.chargeW, 0.02);
        TestKit.isTrue("sysfs 负电流 → valid=true", r.valid);
    }
}
