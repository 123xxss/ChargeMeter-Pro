package com.dsh.powermeter.core;

import android.os.BatteryManager;

/**
 * 充电读数归一化。这里是 PRD「Bug 1 / Bug 3」的核心修复层：
 *
 * 1. 电流符号：以充电状态为准，不硬编码「充电为正」或「充电为负」；
 * 2. 单位：Android 文档中 CURRENT_NOW 为 µA，但个别 ROM 上报 mA，按功率合理性识别；
 * 3. 双电芯：POWER_MAX >= 50W 或（双电芯品牌且电流已达高功率区间）时按 2S 修正；
 * 4. 越界过滤：电压、电流、功率、温度超范围时判为无效读数，不输出错误数值；
 * 5. 增强读取：标准 sysfs 节点可读时优先使用，但仍执行符号归一化与越界过滤。
 *
 * 该类为纯 JVM 可测代码（仅使用 BatteryManager 的编译期常量）。
 */
public final class ChargeNormalizer {

    public static final int UNIT_UNKNOWN = 0;
    public static final int UNIT_MICRO = 1;
    public static final int UNIT_MILLI = 2;

    public static final int DUAL_AUTO = 0;
    public static final int DUAL_FORCE = 1;
    public static final int DUAL_OFF = 2;

    private static final double EPS_NEAR_ZERO_A = 0.02;   // 20mA
    private static final double MAX_PLAUSIBLE_A = 30.0;
    private static final double MAX_PLAUSIBLE_W = 300.0;
    private static final double MIN_CELL_V = 2.5;
    private static final double MAX_CELL_V = 5.0;
    private static final double MIN_PACK_V = 5.0;
    private static final double MAX_PACK_V = 10.5;

    private int preferredUnit;
    private int milliStreak;

    public ChargeNormalizer(int unitHint) {
        this.preferredUnit = unitHint == UNIT_MILLI ? UNIT_MILLI : UNIT_MICRO;
    }

    public int preferredUnit() {
        return preferredUnit;
    }

    public NormalizedReading normalize(RawBattery raw, int dualMode, boolean dualCellBrand) {
        NormalizedReading.Builder out = NormalizedReading.builder()
                .timestampMs(raw.timestampMs)
                .plugged(raw.isPlugged())
                .level(raw.level)
                .currentSupported(raw.currentSupported
                        || (raw.hasSysfs() && !Double.isNaN(raw.sysfsCurrentA)))
                .powerMaxW(raw.powerMaxW)
                .rawVoltageV(raw.voltageMv / 1000.0)
                .rawCurrent(raw.currentRaw)
                .chargeCounterUah(raw.chargeCounterUah)
                .temperatureC(readTemperature(raw));

        boolean plugged = raw.isPlugged();
        boolean chargingStatus = raw.isChargingStatus();
        boolean fullStatus = raw.isFullStatus();
        boolean full = fullStatus || (plugged && raw.level >= 100);

        // 1) 电压 / 电芯数。增强路径优先；否则用标准广播电压 + 双电芯判定。
        Voltage voltage = resolveVoltage(raw, dualMode, dualCellBrand);
        if (voltage == null) {
            return invalid(out, raw, plugged, full, chargingStatus, "电压读数异常");
        }
        out.voltageV(voltage.packV)
                .cells(voltage.cells)
                .voltageCorrected(voltage.corrected)
                .enhanced(voltage.enhanced);

        // 2) 电流单位与大小。
        Current current = resolveCurrent(raw, voltage, chargingStatus);
        if (current == null) {
            return invalid(out, raw, plugged, full, chargingStatus,
                    raw.currentSupported ? "电流读数异常" : "本机未开放电流接口");
        }
        out.unitUsed(current.unit);
        double absA = current.amps;

        // 3) 符号归一化：充电中读数为负则反转；放电/未充电读数取负。
        double signedA;
        if (chargingStatus) {
            signedA = Math.abs(absA);          // 充电中，电流流入电池
        } else if (!plugged) {
            signedA = -Math.abs(absA);         // 放电中，电流流出电池
        } else {
            // 已接电源但系统未在充电（暂停/已充满）：方向不可靠，按放电口径处理。
            signedA = -Math.abs(absA);
        }

        boolean nearZero = chargingStatus && Math.abs(absA) <= EPS_NEAR_ZERO_A;
        double chargeW = chargingStatus ? Math.max(0, voltage.packV * signedA) : 0;

        // 4) 越界过滤。
        String invalidReason = null;
        if (!Double.isFinite(voltage.packV) || voltage.packV <= 0) {
            invalidReason = "电压读数异常";
        } else if (!Double.isFinite(absA) || absA > MAX_PLAUSIBLE_A) {
            invalidReason = "电流读数异常";
        } else if (chargingStatus && nearZero) {
            invalidReason = "电流接近零";
        } else if (chargeW > MAX_PLAUSIBLE_W) {
            invalidReason = "功率读数异常";
        }

        ChargeState state;
        if (!plugged) {
            state = ChargeState.UNPLUGGED;
        } else if (full) {
            state = ChargeState.FULL;
        } else if (chargingStatus) {
            state = invalidReason == null ? ChargeState.CHARGING : ChargeState.UNAVAILABLE;
        } else {
            state = ChargeState.PAUSED;
        }

        boolean valid = invalidReason == null && plugged && chargingStatus;
        return out
                .valid(valid)
                .invalidReason(invalidReason)
                .state(state)
                .currentA(signedA)
                .chargeW(valid ? chargeW : 0)
                .netW(voltage.packV * signedA)
                .nearZero(nearZero)
                .build();
    }

    private NormalizedReading invalid(NormalizedReading.Builder out, RawBattery raw,
                                      boolean plugged, boolean full, boolean charging,
                                      String reason) {
        ChargeState state;
        if (!plugged) {
            state = ChargeState.UNPLUGGED;
        } else if (full) {
            state = ChargeState.FULL;
        } else {
            state = ChargeState.UNAVAILABLE;
        }
        return out.valid(false)
                .invalidReason(reason)
                .state(state)
                .currentA(0)
                .chargeW(0)
                .netW(0)
                .build();
    }

    private double readTemperature(RawBattery raw) {
        double t = Double.NaN;
        if (raw.hasSysfs() && !Double.isNaN(raw.sysfsTempC)) {
            t = raw.sysfsTempC;
        } else if (raw.temperatureDeciC != Integer.MIN_VALUE) {
            t = raw.temperatureDeciC / 10.0;
        }
        if (!Double.isFinite(t) || t < -20 || t > 90) return Double.NaN;
        return t;
    }

    // ------------------------------------------------------------ 电压 / 双电芯

    private static final class Voltage {
        final double packV;
        final int cells;
        final boolean corrected;
        final boolean enhanced;

        Voltage(double packV, int cells, boolean corrected, boolean enhanced) {
            this.packV = packV;
            this.cells = cells;
            this.corrected = corrected;
            this.enhanced = enhanced;
        }
    }

    private Voltage resolveVoltage(RawBattery raw, int dualMode, boolean dualCellBrand) {
        // 增强路径：sysfs voltage_now 一般就是整包电压
        if (raw.hasSysfs()) {
            double v = raw.sysfsVoltageV;
            if (v >= MIN_CELL_V && v <= MAX_PACK_V) {
                boolean corrected = false;
                int cells;
                if (v >= MIN_PACK_V) {
                    cells = clampCells((int) Math.max(2, Math.round(v / 3.85)));
                } else {
                    cells = 1;
                    boolean dualHint = raw.likelyDualCell
                            || (!Double.isNaN(raw.sysfsVoltageMaxV) && raw.sysfsVoltageMaxV >= 7.0)
                            || raw.powerMaxW >= 50;
                    if (dualMode != DUAL_OFF && dualHint) {
                        v *= 2;
                        cells = 2;
                        corrected = true;
                    }
                }
                if (v >= MIN_CELL_V && v <= MAX_PACK_V) {
                    return new Voltage(v, cells, corrected, true);
                }
            }
            // 增强节点数值异常时无感回退标准路径
        }

        double cellV = raw.voltageMv / 1000.0;
        if (!(cellV >= MIN_CELL_V && cellV <= MAX_PACK_V)) return null;

        if (cellV >= MIN_PACK_V) {
            // 系统已经上报整包电压（PRD 以 5V 为单电芯/整包分界），不再乘电芯数；
            // 2S 整包在低电量时可能接近 5V，电芯数至少按 2 处理。
            int cells = clampCells((int) Math.max(2, Math.round(cellV / 3.85)));
            return new Voltage(cellV, cells, false, false);
        }

        boolean dual;
        if (dualMode == DUAL_FORCE) {
            dual = true;
        } else if (dualMode == DUAL_OFF) {
            dual = false;
        } else {
            dual = autoDual(raw, dualCellBrand, cellV);
        }
        if (dual) {
            double pack = cellV * 2;
            if (pack > MAX_PACK_V) return null;
            return new Voltage(pack, 2, true, false);
        }
        return new Voltage(cellV, 1, false, false);
    }

    /**
     * 自动双电芯判定：
     * - POWER_MAX >= 50W：PRD 指定条件，直接判定为 2S；
     * - 双电芯品牌 + 单电芯电压 + 明显高功率电流（>=5A）：按 2S 修正；
     * 低功率充电不乘 2，避免 18W 单电芯机型被翻倍。
     */
    private boolean autoDual(RawBattery raw, boolean dualCellBrand, double cellV) {
        if (raw.likelyDualCell) return true;
        if (!Double.isNaN(raw.sysfsVoltageMaxV) && raw.sysfsVoltageMaxV >= 7.0) return true;
        if (raw.powerMaxW >= 50) return true;
        if (!dualCellBrand) return false;
        if (cellV < MIN_CELL_V || cellV > MAX_CELL_V) return false;
        // 参考 ChargeMeter 的已验证做法：vivo/OPPO/小米双电芯品牌默认按 2S 计算，
        // 不再要求当前电流必须 >=5A（低功率充电时也能给出整包功率）。
        // 单电芯异常机型可在设置中关闭“双电芯修正”。
        return true;
    }

    private boolean plausibleAmps(double amps, double cellV) {
        if (!Double.isFinite(amps) || amps < 0 || amps > MAX_PLAUSIBLE_A) return false;
        double w = amps * (cellV <= 0 ? 3.85 : cellV);
        return w <= MAX_PLAUSIBLE_W;
    }

    // ------------------------------------------------------------ 电流单位

    private static final class Current {
        final double amps;   // 绝对值
        final int unit;

        Current(double amps, int unit) {
            this.amps = amps;
            this.unit = unit;
        }
    }

    private Current resolveCurrent(RawBattery raw, Voltage voltage, boolean charging) {
        // 增强电流只在标准 BatteryManager 不提供电流时兜底使用，
        // 避免 sysfs 节点的非标准单位把正常读数带偏。
        if (!raw.currentSupported) {
            if (raw.hasSysfs() && !Double.isNaN(raw.sysfsCurrentA)) {
                double a = Math.abs(raw.sysfsCurrentA);
                if (Double.isFinite(a) && a <= MAX_PLAUSIBLE_A) {
                    return new Current(a, UNIT_MICRO);
                }
            }
            return null;
        }

        double rawAbs = Math.abs((double) raw.currentRaw);
        if (!Double.isFinite(rawAbs)) return null;
        if (rawAbs == 0) return new Current(0, preferredUnit);

        double micro = rawAbs / 1_000_000.0;
        double milli = rawAbs / 1_000.0;
        boolean microPlausible = plausibleAmps(micro, voltage.packV / Math.max(1, voltage.cells));
        boolean milliPlausible = plausibleAmps(milli, voltage.packV / Math.max(1, voltage.cells));

        if (preferredUnit == UNIT_MILLI) {
            if (milliPlausible) return new Current(milli, UNIT_MILLI);
            if (microPlausible) {
                preferredUnit = UNIT_MICRO;
                return new Current(micro, UNIT_MICRO);
            }
            return null;
        }

        // 默认按 Android 标准的 µA 解释
        if (!microPlausible) {
            if (milliPlausible) {
                preferredUnit = UNIT_MILLI;
                return new Current(milli, UNIT_MILLI);
            }
            return null;
        }

        // 少数 ROM 上报 mA：充电中按 µA 解释功率长期低到不可能，且按 mA 解释合理时切换。
        // 连续 4 个采样一致才切换，避免涓流阶段误判。
        if (charging && rawAbs >= 100 && rawAbs <= 30_000
                && micro * Math.max(1, voltage.cells) * 3.85 < 0.15
                && milli <= MAX_PLAUSIBLE_A) {
            double wMilli = milli * voltage.packV;
            boolean milliPowerPlausible = wMilli >= 0.1 && wMilli <= MAX_PLAUSIBLE_W;
            if (milliPowerPlausible) {
                milliStreak++;
                if (milliStreak >= 4) {
                    preferredUnit = UNIT_MILLI;
                    return new Current(milli, UNIT_MILLI);
                }
            } else {
                milliStreak = 0;
            }
        } else {
            milliStreak = 0;
        }
        return new Current(micro, UNIT_MICRO);
    }

    private static int clampCells(int cells) {
        if (cells < 1) return 1;
        if (cells > 4) return 4;
        return cells;
    }
}
