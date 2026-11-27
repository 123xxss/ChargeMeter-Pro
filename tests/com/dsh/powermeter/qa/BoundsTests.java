package com.dsh.powermeter.qa;

import com.dsh.powermeter.core.ChargeNormalizer;
import com.dsh.powermeter.core.ChargeState;
import com.dsh.powermeter.core.NormalizedReading;
import com.dsh.powermeter.core.RawBattery;

/**
 * PRD 要求：电压 / 电流 / 温度越界时判为无效读数，绝不显示错误功率数值。
 */
final class BoundsTests {

    private static final int AUTO = ChargeNormalizer.DUAL_AUTO;

    private BoundsTests() {
    }

    static void run() {
        voltageTooLow();
        voltageTooHigh();
        currentTooHigh();
        currentUnsupported();
        voltage5800TreatedAsPack();
        packVoltageBoundary();
        temperatureBounds();
        sysfsTemperaturePriority();
    }

    /** 1.5V 低于单电芯下限（2.5V）→ 无效读数。 */
    private static void voltageTooLow() {
        ChargeNormalizer n = new ChargeNormalizer(ChargeNormalizer.UNIT_UNKNOWN);
        NormalizedReading r = n.normalize(
                Fixtures.charging(2_000_000L).voltageMv(1500).build(), AUTO, false);

        TestKit.isFalse("电压 1.5V 越界 → valid=false", r.valid);
        TestKit.eqDouble("电压越界 → chargeW=0（不输出错误功率）", 0.0, r.chargeW, 1e-9);
        TestKit.eqDouble("电压越界 → netW=0", 0.0, r.netW, 1e-9);
        TestKit.eqDouble("电压越界 → currentA=0", 0.0, r.currentA, 1e-9);
        TestKit.eqStr("电压越界 → 状态「读数不可用」",
                ChargeState.UNAVAILABLE.name(), r.state.name());
        TestKit.contains("电压越界 → 原因说明指向电压", "电压", r.invalidReason);
    }

    /** 12V 超过整包上限（10.5V）→ 无效读数。 */
    private static void voltageTooHigh() {
        ChargeNormalizer n = new ChargeNormalizer(ChargeNormalizer.UNIT_UNKNOWN);
        NormalizedReading r = n.normalize(
                Fixtures.charging(2_000_000L).voltageMv(12000).build(), AUTO, false);

        TestKit.isFalse("电压 12V 越界 → valid=false", r.valid);
        TestKit.eqDouble("电压 12V 越界 → chargeW=0", 0.0, r.chargeW, 1e-9);
        TestKit.contains("电压 12V 越界 → 原因说明指向电压", "电压", r.invalidReason);
    }

    /** 40A 超过 30A 合理上限 → 无效读数，不能算出 172W 之类的错误功率。 */
    private static void currentTooHigh() {
        ChargeNormalizer n = new ChargeNormalizer(ChargeNormalizer.UNIT_UNKNOWN);
        NormalizedReading r = n.normalize(
                Fixtures.charging(40_000_000L).voltageMv(4300).build(), AUTO, false);

        TestKit.isFalse("电流 40A 越界 → valid=false", r.valid);
        TestKit.eqDouble("电流 40A 越界 → chargeW=0", 0.0, r.chargeW, 1e-9);
        TestKit.contains("电流越界 → 原因说明指向电流", "电流", r.invalidReason);
        TestKit.eqStr("电流越界 → 状态「读数不可用」",
                ChargeState.UNAVAILABLE.name(), r.state.name());
    }

    /** 系统完全不提供电流接口且无 sysfs → 明确说明，不给假功率。 */
    private static void currentUnsupported() {
        ChargeNormalizer n = new ChargeNormalizer(ChargeNormalizer.UNIT_UNKNOWN);
        RawBattery raw = Fixtures.base()
                .status(Fixtures.STATUS_CHARGING)
                .plugged(Fixtures.PLUGGED_AC)
                .voltageMv(4300)
                .currentSupported(false)
                .build();
        NormalizedReading r = n.normalize(raw, AUTO, false);

        TestKit.isFalse("不提供电流接口 → valid=false", r.valid);
        TestKit.eqDouble("不提供电流接口 → chargeW=0", 0.0, r.chargeW, 1e-9);
        TestKit.notNull("不提供电流接口 → 有 invalidReason", r.invalidReason);
        TestKit.contains("不提供电流接口 → 原因说明接口未开放", "未开放", r.invalidReason);
        TestKit.eqStr("不提供电流接口 → 状态「读数不可用」",
                ChargeState.UNAVAILABLE.name(), r.state.name());
    }

    /**
     * 5.8V 落在整包电压区间（5.0–10.5V）内 → 按整包解释，不能再乘 2 变成 11.6V。
     * 核心层修复：resolveVoltage 接受 2.5–10.5V，整包 ≥5.0V 时 cells 至少 2、不再翻倍。
     */
    private static void voltage5800TreatedAsPack() {
        ChargeNormalizer n = new ChargeNormalizer(ChargeNormalizer.UNIT_UNKNOWN);
        NormalizedReading r = n.normalize(
                Fixtures.charging(2_000_000L).voltageMv(5800).build(), AUTO, false);

        TestKit.eqDouble("5.8V → 电压按整包取 5.8V", 5.8, r.voltageV, 1e-6);
        TestKit.isTrue("5.8V 整包 → cells≥2", r.cells >= 2);
        TestKit.isFalse("5.8V 整包 → 不标记电压修正", r.voltageCorrected);
        TestKit.eqDouble("5.8V × 2A → chargeW=11.6（未翻倍成 23.2）", 11.6, r.chargeW, 1e-6);
        TestKit.isTrue("5.8V → valid=true", r.valid);
    }

    /** 5.0V 正好是「已上报整包电压」的下限 → 按整包处理，不能再乘 2。 */
    private static void packVoltageBoundary() {
        ChargeNormalizer n = new ChargeNormalizer(ChargeNormalizer.UNIT_UNKNOWN);
        NormalizedReading r = n.normalize(
                Fixtures.charging(2_000_000L).voltageMv(5000).build(), AUTO, false);

        TestKit.eqDouble("5.0V 边界 → 电压保持 5.0", 5.0, r.voltageV, 1e-6);
        TestKit.isFalse("5.0V 边界 → 不标记电压修正", r.voltageCorrected);
        TestKit.isTrue("5.0V 边界 → 按整包解释，cells≥2", r.cells >= 2);
        TestKit.eqDouble("5.0V × 2A → chargeW=10.0（未翻倍）", 10.0, r.chargeW, 1e-6);
        TestKit.isTrue("5.0V 边界 → 读数有效", r.valid);
    }

    /** 温度：35℃ 正常；100℃ / -30℃ 越界置 NaN。 */
    private static void temperatureBounds() {
        ChargeNormalizer n = new ChargeNormalizer(ChargeNormalizer.UNIT_UNKNOWN);
        NormalizedReading normal = n.normalize(
                Fixtures.charging(2_000_000L).voltageMv(4300).temperatureDeciC(350).build(),
                AUTO, false);
        TestKit.eqDouble("温度 350 deci℃ → 35.0℃", 35.0, normal.temperatureC, 1e-9);

        ChargeNormalizer n2 = new ChargeNormalizer(ChargeNormalizer.UNIT_UNKNOWN);
        NormalizedReading hot = n2.normalize(
                Fixtures.charging(2_000_000L).voltageMv(4300).temperatureDeciC(1000).build(),
                AUTO, false);
        TestKit.isNaN("温度 100℃ 越界 → temperatureC=NaN", hot.temperatureC);

        ChargeNormalizer n3 = new ChargeNormalizer(ChargeNormalizer.UNIT_UNKNOWN);
        NormalizedReading cold = n3.normalize(
                Fixtures.charging(2_000_000L).voltageMv(4300).temperatureDeciC(-300).build(),
                AUTO, false);
        TestKit.isNaN("温度 -30℃ 越界 → temperatureC=NaN", cold.temperatureC);

        ChargeNormalizer n4 = new ChargeNormalizer(ChargeNormalizer.UNIT_UNKNOWN);
        RawBattery noTemp = RawBattery.builder()
                .timestampMs(0L)
                .status(Fixtures.STATUS_CHARGING)
                .plugged(Fixtures.PLUGGED_AC)
                .level(50)
                .voltageMv(4300)
                .currentRaw(2_000_000L)
                .currentSupported(true)
                .build();
        NormalizedReading r4 = n4.normalize(noTemp, AUTO, false);
        TestKit.isNaN("未上报温度 → temperatureC=NaN（不被当成 0℃）", r4.temperatureC);
    }

    /** 增强路径温度优先级：sysfs 温度可用时优先于广播温度。 */
    private static void sysfsTemperaturePriority() {
        ChargeNormalizer n = new ChargeNormalizer(ChargeNormalizer.UNIT_UNKNOWN);
        RawBattery raw = RawBattery.builder()
                .timestampMs(0L)
                .status(Fixtures.STATUS_CHARGING)
                .plugged(Fixtures.PLUGGED_AC)
                .level(50)
                .voltageMv(4300)
                .temperatureDeciC(300)
                .currentSupported(false)
                .powerMaxW(0)
                .sysfsVoltageV(4.3)
                .sysfsCurrentA(2.0)
                .sysfsTempC(38.5)
                .build();
        NormalizedReading r = n.normalize(raw, AUTO, false);

        TestKit.eqDouble("sysfs 温度 38.5℃ 优先于广播 30℃", 38.5, r.temperatureC, 1e-9);
        TestKit.isTrue("sysfs 增强路径 → enhanced=true", r.enhanced);
        TestKit.eqDouble("sysfs 4.3V × 2.0A → chargeW=8.6", 8.6, r.chargeW, 1e-6);
        TestKit.isTrue("sysfs 增强路径 → valid=true", r.valid);
    }
}
