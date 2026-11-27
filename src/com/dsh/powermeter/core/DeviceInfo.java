package com.dsh.powermeter.core;

import android.os.Build;

/** 设备与系统版本信息；仅读取 Build，不申请任何权限。 */
public final class DeviceInfo {

    private DeviceInfo() {
    }

    public static String manufacturer() {
        String v = Build.MANUFACTURER;
        return v == null || v.isEmpty() ? "--" : v;
    }

    public static String brand() {
        String v = Build.BRAND;
        return v == null || v.isEmpty() ? "--" : v;
    }

    public static String model() {
        String v = Build.MODEL;
        return v == null || v.isEmpty() ? "--" : v;
    }

    public static String device() {
        String v = Build.DEVICE;
        return v == null || v.isEmpty() ? "--" : v;
    }

    public static String androidVersion() {
        String release = Build.VERSION.RELEASE;
        return (release == null ? "--" : release) + "（API " + Build.VERSION.SDK_INT + "）";
    }

    public static String romVersion() {
        String v = Build.DISPLAY;
        return v == null || v.isEmpty() ? "--" : v;
    }

    public static String abi() {
        String[] abis = Build.SUPPORTED_ABIS;
        return abis == null || abis.length == 0 ? "--" : abis[0];
    }

    public static String compatibilityNote() {
        String m = (manufacturer() + " " + brand()).toLowerCase(java.util.Locale.ROOT);
        if (m.contains("huawei") || m.contains("honor")) {
            return "华为/荣耀充电协议差异较大，功率可能偏差，可用设置中的功率校准修正；"
                    + "本应用不包含针对华为/荣耀的私有快充适配。";
        }
        return "通用 Android 适配；不包含 iOS/苹果设备。功率以系统 BatteryManager 与品牌电芯数为准，"
                + "个别机型可用功率校准修正。";
    }
}
