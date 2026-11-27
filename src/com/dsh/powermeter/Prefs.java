package com.dsh.powermeter;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 本地设置。所有开关默认值与 PRD 一致：
 * - 总开关默认开启；
 * - 通知默认开启；
 * - 实时功耗检测默认关闭；
 * - 增强读取（实验）默认关闭。
 *
 * 本类不做任何网络/上传行为，设置只保存在本机 SharedPreferences。
 */
public final class Prefs {

    private static final String FILE = "powermeter_prefs";

    public static final int DUAL_AUTO = 0;
    public static final int DUAL_FORCE = 1;
    public static final int DUAL_OFF = 2;

    private final SharedPreferences sp;

    private Prefs(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    private static volatile Prefs instance;

    public static Prefs get(Context context) {
        if (instance == null) {
            synchronized (Prefs.class) {
                if (instance == null) instance = new Prefs(context);
            }
        }
        return instance;
    }

    public SharedPreferences raw() {
        return sp;
    }

    private boolean getBool(String key, boolean def) {
        try {
            return sp.getBoolean(key, def);
        } catch (Throwable t) {
            return def;
        }
    }

    private void setBool(String key, boolean value) {
        try {
            sp.edit().putBoolean(key, value).apply();
        } catch (Throwable ignored) {
        }
    }

    // ---------------------------------------------------------------- 显示开关

    public boolean masterEnabled() {
        return getBool("master_enabled", true);
    }

    public void setMasterEnabled(boolean enabled) {
        setBool("master_enabled", enabled);
    }

    public boolean notificationEnabled() {
        return getBool("notification_enabled", true);
    }

    public void setNotificationEnabled(boolean enabled) {
        setBool("notification_enabled", enabled);
    }

    public boolean realtimePowerEnabled() {
        return getBool("realtime_power_enabled", false);
    }

    public void setRealtimePowerEnabled(boolean enabled) {
        setBool("realtime_power_enabled", enabled);
    }

    public boolean showTemperature() {
        return getBool("show_temperature", true);
    }

    public void setShowTemperature(boolean show) {
        setBool("show_temperature", show);
    }

    public boolean showEstimate() {
        return getBool("show_estimate", true);
    }

    public void setShowEstimate(boolean show) {
        setBool("show_estimate", show);
    }

    public boolean showTier() {
        return getBool("show_tier", true);
    }

    public void setShowTier(boolean show) {
        setBool("show_tier", show);
    }

    // ---------------------------------------------------------------- 数据口径

    /** 0=自动，1=强制双电芯，2=关闭修正。 */
    public int dualCellMode() {
        try {
            return sp.getInt("dual_cell_mode", DUAL_AUTO);
        } catch (Throwable t) {
            return DUAL_AUTO;
        }
    }

    public void setDualCellMode(int mode) {
        try {
            sp.edit().putInt("dual_cell_mode", mode).apply();
        } catch (Throwable ignored) {
        }
    }

    /** 增强读取：直接读取系统标准 sysfs 电池节点，只读、无需任何额外权限。 */
    public boolean enhancedReadEnabled() {
        return getBool("enhanced_read_enabled", false);
    }

    public void setEnhancedReadEnabled(boolean enabled) {
        setBool("enhanced_read_enabled", enabled);
    }

    /** Shizuku 只读增强读取（实验性，默认关闭）。 */
    public boolean shizukuReadEnabled() {
        return getBool("shizuku_read_enabled", false);
    }

    public void setShizukuReadEnabled(boolean enabled) {
        setBool("shizuku_read_enabled", enabled);
    }

    /** 功率校准倍率（参考 ChargeMeter 的校准思路），默认 1.0。 */
    public double powerRatio() {
        return getFloat("power_ratio", 1.0f);
    }

    public void setPowerRatio(double ratio) {
        try {
            float r = (float) Math.max(0.2, Math.min(5.0, ratio));
            sp.edit().putFloat("power_ratio", r).apply();
        } catch (Throwable ignored) {
        }
    }

    /** 电流单位记忆：0 未知，1 µA，2 mA。用于修正少数 ROM 的电流单位上报差异。 */
    public int currentUnitHint() {
        try {
            return sp.getInt("current_unit", 0);
        } catch (Throwable t) {
            return 0;
        }
    }

    public void setCurrentUnitHint(int unit) {
        try {
            sp.edit().putInt("current_unit", unit).apply();
        } catch (Throwable ignored) {
        }
    }

    public double lowTierWatts() {
        return getFloat("tier_low_w", 5f);
    }

    public double highTierWatts() {
        return getFloat("tier_high_w", 30f);
    }

    private float getFloat(String key, float def) {
        try {
            return sp.getFloat(key, def);
        } catch (Throwable t) {
            return def;
        }
    }

    // ---------------------------------------------------------------- 引导状态

    public boolean guideShown() {
        return getBool("guide_shown", false);
    }

    public void markGuideShown() {
        setBool("guide_shown", true);
    }

    public boolean notificationAsked() {
        return getBool("notification_asked", false);
    }

    public void markNotificationAsked() {
        setBool("notification_asked", true);
    }

    // ---------------------------------------------------------------- 上一次充电摘要

    public boolean hasLastSession() {
        return getBool("last_session_valid", false);
    }

    public void saveLastSession(long endTimeMs, long durationMs, double peakW, double avgW,
                                int startLevel, int endLevel) {
        try {
            sp.edit()
                    .putBoolean("last_session_valid", true)
                    .putLong("last_session_end", endTimeMs)
                    .putLong("last_session_duration", durationMs)
                    .putFloat("last_session_peak", (float) peakW)
                    .putFloat("last_session_avg", (float) avgW)
                    .putInt("last_session_start_level", startLevel)
                    .putInt("last_session_end_level", endLevel)
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    public long lastSessionEnd() {
        try {
            return sp.getLong("last_session_end", 0L);
        } catch (Throwable t) {
            return 0L;
        }
    }

    public long lastSessionDuration() {
        try {
            return sp.getLong("last_session_duration", 0L);
        } catch (Throwable t) {
            return 0L;
        }
    }

    public double lastSessionPeak() {
        return getFloat("last_session_peak", 0f);
    }

    public double lastSessionAvg() {
        return getFloat("last_session_avg", 0f);
    }

    public int lastSessionStartLevel() {
        try {
            return sp.getInt("last_session_start_level", -1);
        } catch (Throwable t) {
            return -1;
        }
    }

    public int lastSessionEndLevel() {
        try {
            return sp.getInt("last_session_end_level", -1);
        } catch (Throwable t) {
            return -1;
        }
    }
}
