package com.dsh.powermeter.core;

import java.util.Locale;

/**
 * 电芯数自动识别（双芯 / 单芯）。
 *
 * 判定顺序（高置信度优先）：
 * 1. sysfs voltage_max ≥ 6V → 2 芯；3–5V → 1 芯；
 * 2. 系统上报电压 > 5V → 整包电压，按 2 芯；
 * 3. 已知双电芯机型表；
 * 4. 品牌默认（vivo/OPPO/小米等 2 芯，其余 1 芯），标记为低置信度。
 *
 * 说明：Android 没有公开的“电芯数”API；如果系统已上报单芯电压且 voltage_max
 * 不可读，仅靠电压本身无法区分单/双芯，只能按机型/品牌默认，用户可手动覆盖。
 */
public final class CellDetector {

    public static final class Result {
        public final int cells;
        public final String reason;
        public final boolean confident;

        Result(int cells, String reason, boolean confident) {
            this.cells = cells;
            this.reason = reason == null ? "" : reason;
            this.confident = confident;
        }
    }

    private CellDetector() {
    }

    public static Result detect(RawBattery raw, boolean knownDual, int brandDefault) {
        if (raw == null) return new Result(1, "无读数，按单芯", false);

        double vMax = raw.sysfsVoltageMaxV;
        if (Double.isFinite(vMax) && vMax >= 6.0 && vMax <= 12.0) {
            return new Result(2, String.format(Locale.ROOT,
                    "2 芯 · voltage_max %.2fV", vMax), true);
        }
        if (raw.voltageMv > 5000 && raw.voltageMv <= 10500) {
            return new Result(2, String.format(Locale.ROOT,
                    "2 芯 · 整包电压 %.2fV", raw.voltageMv / 1000.0), true);
        }
        // 已知双芯机型优先于“voltage_max≤5V 视为单芯”：
        // 个别双芯 ROM 的 voltage_max 也按单芯上报 4.4V，不能误判成单芯。
        if (knownDual || raw.likelyDualCell) {
            return new Result(2, "2 芯 · 已知双电芯机型", true);
        }
        if (Double.isFinite(vMax) && vMax >= 3.0 && vMax <= 5.0) {
            return new Result(1, String.format(Locale.ROOT,
                    "1 芯 · voltage_max %.2fV", vMax), true);
        }
        int fallback = brandDefault >= 2 ? 2 : 1;
        return new Result(fallback, fallback + " 芯 · 品牌默认（未读到明确电芯特征）", false);
    }
}
