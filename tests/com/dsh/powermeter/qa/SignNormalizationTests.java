package com.dsh.powermeter.qa;

import com.dsh.powermeter.core.ChargeNormalizer;
import com.dsh.powermeter.core.ChargeState;
import com.dsh.powermeter.core.NormalizedReading;
import com.dsh.powermeter.core.RawBattery;

/**
 * PRD Bug 1：vivo 等 ROM 在充电中把 current_now 上报为负值，
 * 界面不能显示负功率 / 方向错误。符号必须以充电状态为准。
 */
final class SignNormalizationTests {

    private static final int AUTO = ChargeNormalizer.DUAL_AUTO;
    private static final boolean NOT_DUAL_BRAND = false;

    private SignNormalizationTests() {
    }

    static void run() {
        chargingNegativeCurrent();
        chargingPositiveCurrent();
        dischargingPositiveCurrent();
        dischargingNegativeCurrent();
        pluggedButNotCharging();
        chargingNearZeroCurrent();
        fullBattery();
        unpluggedWithCurrent();
    }

    /** vivo 口径：充电中 current_now 为负 → 展示必须为正。 */
    private static void chargingNegativeCurrent() {
        ChargeNormalizer n = new ChargeNormalizer(ChargeNormalizer.UNIT_UNKNOWN);
        RawBattery raw = Fixtures.charging(-2_400_000L).voltageMv(4300).build();
        NormalizedReading r = n.normalize(raw, AUTO, NOT_DUAL_BRAND);

        TestKit.eqDouble("vivo 充电中 current_now=-2.4A → currentA 归一化为 +2.4",
                2.4, r.currentA, 1e-6);
        TestKit.eqDouble("vivo 充电中 -2.4A @4.3V → chargeW=10.32（不能是负功率）",
                10.32, r.chargeW, 1e-6);
        TestKit.eqDouble("vivo 充电中 → netW 为正（流入电池）", 10.32, r.netW, 1e-6);
        TestKit.isTrue("vivo 充电中负电流 → 读数有效", r.valid);
        TestKit.eqStr("vivo 充电中 → 状态「充电中」", ChargeState.CHARGING.name(), r.state.name());
        TestKit.isFalse("vivo 充电中 → 不应标记 nearZero", r.nearZero);
        TestKit.eqDouble("vivo 充电中 → 电压 4.3V 保持单电芯", 4.3, r.voltageV, 1e-6);
    }

    /** 常规口径：充电中 current_now 为正 → 保持为正。 */
    private static void chargingPositiveCurrent() {
        ChargeNormalizer n = new ChargeNormalizer(ChargeNormalizer.UNIT_UNKNOWN);
        RawBattery raw = Fixtures.charging(2_400_000L).voltageMv(4300).build();
        NormalizedReading r = n.normalize(raw, AUTO, NOT_DUAL_BRAND);

        TestKit.eqDouble("充电中 current_now=+2.4A → currentA=+2.4", 2.4, r.currentA, 1e-6);
        TestKit.eqDouble("充电中 +2.4A @4.3V → chargeW=10.32", 10.32, r.chargeW, 1e-6);
        TestKit.isTrue("充电中正电流 → 读数有效", r.valid);
    }

    /** 放电中 current_now 为正（部分 ROM）→ 展示必须为负。 */
    private static void dischargingPositiveCurrent() {
        ChargeNormalizer n = new ChargeNormalizer(ChargeNormalizer.UNIT_UNKNOWN);
        RawBattery raw = Fixtures.base()
                .status(Fixtures.STATUS_DISCHARGING)
                .plugged(0)
                .level(60)
                .voltageMv(4200)
                .currentRaw(1_200_000L)
                .currentSupported(true)
                .build();
        NormalizedReading r = n.normalize(raw, AUTO, NOT_DUAL_BRAND);

        TestKit.eqDouble("放电中 current_now=+1.2A → currentA 归一化为 -1.2",
                -1.2, r.currentA, 1e-6);
        TestKit.eqDouble("放电中 -1.2A @4.2V → netW=-5.04", -5.04, r.netW, 1e-6);
        TestKit.eqDouble("放电中 → chargeW=0（充电功率只在充电中有效）", 0.0, r.chargeW, 1e-9);
        TestKit.isFalse("放电中 → valid=false（无充电功率）", r.valid);
        TestKit.eqStr("放电中 → 状态「未充电」", ChargeState.UNPLUGGED.name(), r.state.name());
    }

    /** 放电中 current_now 为负 → 也归一化为负（方向只由状态决定）。 */
    private static void dischargingNegativeCurrent() {
        ChargeNormalizer n = new ChargeNormalizer(ChargeNormalizer.UNIT_UNKNOWN);
        RawBattery raw = Fixtures.base()
                .status(Fixtures.STATUS_DISCHARGING)
                .plugged(0)
                .level(60)
                .voltageMv(4200)
                .currentRaw(-1_200_000L)
                .currentSupported(true)
                .build();
        NormalizedReading r = n.normalize(raw, AUTO, NOT_DUAL_BRAND);

        TestKit.eqDouble("放电中 current_now=-1.2A → currentA=-1.2", -1.2, r.currentA, 1e-6);
        TestKit.eqDouble("放电中 -1.2A @4.2V → netW=-5.04", -5.04, r.netW, 1e-6);
        TestKit.isFalse("放电中负电流 → valid=false", r.valid);
    }

    /** 已接电源但系统未充电（暂停 / 涓流结束）：方向不可靠，按放电口径，状态为暂停。 */
    private static void pluggedButNotCharging() {
        ChargeNormalizer n = new ChargeNormalizer(ChargeNormalizer.UNIT_UNKNOWN);
        RawBattery raw = Fixtures.base()
                .status(Fixtures.STATUS_NOT_CHARGING)
                .plugged(Fixtures.PLUGGED_AC)
                .level(80)
                .voltageMv(4300)
                .currentRaw(500_000L)
                .currentSupported(true)
                .build();
        NormalizedReading r = n.normalize(raw, AUTO, NOT_DUAL_BRAND);

        TestKit.eqStr("已插电但状态 NOT_CHARGING → 状态「已暂停」",
                ChargeState.PAUSED.name(), r.state.name());
        TestKit.eqDouble("已插电暂停 → chargeW=0，不显示虚假充电功率", 0.0, r.chargeW, 1e-9);
        TestKit.isFalse("已插电暂停 → valid=false", r.valid);
        TestKit.eqDouble("已插电暂停 → currentA 取放电口径（-0.5）", -0.5, r.currentA, 1e-6);
    }

    /** 充电中电流≈0（5mA）：标记 nearZero，不得输出错误功率数值。 */
    private static void chargingNearZeroCurrent() {
        ChargeNormalizer n = new ChargeNormalizer(ChargeNormalizer.UNIT_UNKNOWN);
        RawBattery raw = Fixtures.charging(5_000L).voltageMv(4300).build();
        NormalizedReading r = n.normalize(raw, AUTO, NOT_DUAL_BRAND);

        TestKit.isTrue("充电中 5mA → nearZero=true", r.nearZero);
        TestKit.isFalse("充电中 5mA → valid=false（不给错误功率）", r.valid);
        TestKit.eqDouble("充电中 5mA → chargeW=0", 0.0, r.chargeW, 1e-9);
        TestKit.notNull("充电中 5mA → 带 invalidReason 说明原因", r.invalidReason);
        TestKit.contains("充电中 5mA → 原因说明为电流接近零", "零", r.invalidReason);
        TestKit.eqStr("充电中 5mA → 状态「读数不可用」",
                ChargeState.UNAVAILABLE.name(), r.state.name());
        TestKit.range("充电中 5mA → currentA 仍在 nearZero 阈值内", -0.02, 0.02, r.currentA);
    }

    /** 充满：状态 FULL，无充电功率。 */
    private static void fullBattery() {
        ChargeNormalizer n = new ChargeNormalizer(ChargeNormalizer.UNIT_UNKNOWN);
        RawBattery raw = Fixtures.base()
                .status(Fixtures.STATUS_FULL)
                .plugged(Fixtures.PLUGGED_AC)
                .level(100)
                .voltageMv(4300)
                .currentRaw(500_000L)
                .currentSupported(true)
                .build();
        NormalizedReading r = n.normalize(raw, AUTO, NOT_DUAL_BRAND);

        TestKit.eqStr("level=100 且状态 FULL → 状态「已充满」",
                ChargeState.FULL.name(), r.state.name());
        TestKit.eqDouble("已充满 → chargeW=0", 0.0, r.chargeW, 1e-9);
        TestKit.isFalse("已充满 → valid=false", r.valid);
        TestKit.isFalse("已充满 → 不标记 nearZero（不是电流读数问题）", r.nearZero);
    }

    /** 未插电但有电流（放电）→ UNPLUGGED，currentA 为负。 */
    private static void unpluggedWithCurrent() {
        ChargeNormalizer n = new ChargeNormalizer(ChargeNormalizer.UNIT_UNKNOWN);
        RawBattery raw = Fixtures.base()
                .status(Fixtures.STATUS_UNKNOWN)
                .plugged(0)
                .level(70)
                .voltageMv(4000)
                .currentRaw(300_000L)
                .currentSupported(true)
                .build();
        NormalizedReading r = n.normalize(raw, AUTO, NOT_DUAL_BRAND);

        TestKit.eqStr("未插电 → 状态「未充电」", ChargeState.UNPLUGGED.name(), r.state.name());
        TestKit.eqDouble("未插电放电 0.3A @4.0V → currentA=-0.3", -0.3, r.currentA, 1e-6);
        TestKit.eqDouble("未插电放电 → netW=-1.2", -1.2, r.netW, 1e-6);
        TestKit.eqDouble("未插电 → chargeW=0", 0.0, r.chargeW, 1e-9);
    }
}
