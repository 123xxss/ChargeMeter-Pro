package com.dsh.powermeter;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import java.util.Locale;

/**
 * 三家重点 ROM 的识别、后台白名单深链与引导文案。
 *
 * 所有跳转都按「候选组件 → 系统电池优化页 → 应用详情页」逐级回退，
 * 任何一步失败都不会让用户卡住；不申请任何额外权限。
 */
public final class BrandCompat {

    public enum Brand {
        VIVO, OPPO, XIAOMI, HUAWEI, HONOR, SAMSUNG, GOOGLE, OTHER
    }

    public static final String MIUI_QUICK_CHARGE_ACTION = "miui.intent.action.ACTION_QUICK_CHARGE_TYPE";
    public static final String MIUI_EXTRA_POWER_MAX = "miui.intent.extra.POWER_MAX";
    public static final String MIUI_EXTRA_QUICK_CHARGE_TYPE = "miui.intent.extra.QUICK_CHARGE_TYPE";

    private BrandCompat() {
    }

    private static String raw() {
        String m = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER;
        String b = Build.BRAND == null ? "" : Build.BRAND;
        String model = Build.MODEL == null ? "" : Build.MODEL;
        return (m + " " + b + " " + model).toLowerCase(Locale.ROOT);
    }

    public static Brand brand() {
        String s = raw();
        if (s.contains("vivo") || s.contains("iqoo")) return Brand.VIVO;
        if (s.contains("oppo") || s.contains("oneplus") || s.contains("realme")
                || s.contains("oplus")) return Brand.OPPO;
        if (s.contains("xiaomi") || s.contains("redmi") || s.contains("poco")) return Brand.XIAOMI;
        if (s.contains("huawei")) return Brand.HUAWEI;
        if (s.contains("honor")) return Brand.HONOR;
        if (s.contains("samsung")) return Brand.SAMSUNG;
        if (s.contains("google") || s.contains("pixel")) return Brand.GOOGLE;
        return Brand.OTHER;
    }

    public static String brandKey() {
        switch (brand()) {
            case VIVO: return "vivo";
            case OPPO: return "oppo";
            case XIAOMI: return "xiaomi";
            default: return "other";
        }
    }

    /** 这些品牌的高功率快充机型常见 2S 双电芯（单电芯电压上报，需 ×2 修正）。 */
    public static boolean dualCellFamily() {
        Brand b = brand();
        return b == Brand.VIVO || b == Brand.OPPO || b == Brand.XIAOMI;
    }

    public static boolean isTargetBrand() {
        return dualCellFamily();
    }

    public static String brandDisplayName() {
        switch (brand()) {
            case VIVO: return "vivo / iQOO";
            case OPPO: return "OPPO / 一加 / realme";
            case XIAOMI: return "小米 / Redmi / POCO";
            case HUAWEI: return "华为";
            case HONOR: return "荣耀";
            case SAMSUNG: return "三星";
            case GOOGLE: return "Google";
            default: return "其他品牌";
        }
    }

    /**
     * 参考 ChargeMeter 的推荐电芯数：国产高功率快充品牌默认 2，其余 1。
     * 用户可在设置中覆盖；不是“自动探测”，避免误判。
     */
    public static int recommendedCellCount() {
        Brand b = brand();
        if (b == Brand.VIVO || b == Brand.OPPO || b == Brand.XIAOMI || b == Brand.HONOR) return 2;
        return 1;
    }

    /** 已知/高置信度双电芯机型（g 用于自动修正；未知机型仍可手动强制）。 */
    public static boolean isKnownDualCellDevice() {
        try {
            String id = (Build.MODEL + " " + Build.DEVICE + " " + Build.PRODUCT)
                    .toLowerCase(Locale.ROOT);
            switch (brand()) {
                case VIVO:
                    // iQOO 11/12/Neo 系列、vivo X90/X100 系列等 2S 高功率机型
                    return id.contains("v2339") || id.contains("v2307") || id.contains("v2329")
                            || id.contains("v2352") || id.contains("v2361") || id.contains("v2362")
                            || id.contains("neo9") || id.contains("neo8") || id.contains("neo7")
                            || id.contains("iqoo 12") || id.contains("iqoo 11")
                            || id.contains("x100") || id.contains("x90");
                case OPPO:
                    return id.contains("phk110") || id.contains("phb110")
                            || id.contains("pjd110") || id.contains("cph2447")
                            || id.contains("cph2451") || id.contains("cph2525");
                case XIAOMI:
                    // 小米高功率机型通常有 POWER_MAX 广播；这里只兜底少数已知型号
                    return id.contains("23127pn0cc") || id.contains("23113rkc6c")
                            || id.contains("23049rad8c");
                default:
                    return false;
            }
        } catch (Throwable t) {
            return false;
        }
    }

    /** PRD 12.2 的引导文案。 */
    public static String guideSteps() {
        switch (brand()) {
            case VIVO:
                return "设置 → 电池 → 后台耗电管理 → 允许后台高耗电\n"
                        + "或：设置 → 电池 → 电池优化 → 本应用设为「无限制」。\n\n"
                        + "不设置的话，锁屏或切后台后充电功率可能停止刷新。";
            case OPPO:
                return "设置 → 电池 → 省电设置 → 应用耗电管理 → 本应用 → 完全允许后台行为。\n\n"
                        + "部分 ColorOS 还需要在「应用自启动」中允许本应用。\n"
                        + "不设置的话，锁屏或切后台后充电功率可能停止刷新。";
            case XIAOMI:
                return "设置 → 电池与性能 → 省电模式 → 应用智能省电 → 本应用 → 无限制。\n"
                        + "并关闭「神隐模式」对本应用的限制。\n\n"
                        + "不设置的话，锁屏或切后台后充电功率可能停止刷新。";
            default:
                return "请把本应用的后台限制设为「不限制」，并在最近任务中锁定本应用。\n\n"
                        + "不设置的话，锁屏或切后台后充电功率可能停止刷新。";
        }
    }

    public static boolean batteryOptimizationIgnored(Context context) {
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    /** 依次尝试品牌后台设置页，失败回退系统电池优化页 / 应用详情页。 */
    public static boolean openBackgroundSettings(Activity activity) {
        for (Intent intent : candidateIntents(activity)) {
            if (tryStart(activity, intent)) return true;
        }
        Intent battery = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        battery.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (tryStart(activity, battery)) return true;
        return openAppDetails(activity);
    }

    private static java.util.List<Intent> candidateIntents(Context context) {
        java.util.ArrayList<Intent> list = new java.util.ArrayList<>();
        String pkg = context.getPackageName();
        String label = "";
        try {
            label = context.getApplicationInfo().loadLabel(context.getPackageManager()).toString();
        } catch (Throwable ignored) {
        }
        switch (brand()) {
            case XIAOMI:
                list.add(component("com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"));
                Intent powerKeeper = component("com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.HiddenAppsConfigActivity");
                powerKeeper.putExtra("package_name", pkg);
                powerKeeper.putExtra("package_label", label);
                list.add(powerKeeper);
                list.add(component("com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.PowerHideAppActivity"));
                break;
            case OPPO:
                list.add(component("com.coloros.safecenter",
                        "com.coloros.safecenter.startupapp.StartupAppListActivity"));
                list.add(component("com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"));
                list.add(component("com.oplus.safecenter",
                        "com.oplus.safecenter.startupapp.StartupAppListActivity"));
                list.add(component("com.coloros.oppoguardelf",
                        "com.coloros.oppoguardelf.PowerUsageActivity"));
                list.add(component("com.oneplus.security",
                        "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"));
                break;
            case VIVO:
                list.add(component("com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"));
                list.add(component("com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"));
                list.add(component("com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"));
                list.add(component("com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.PurviewTabActivity"));
                break;
            default:
                break;
        }
        for (Intent i : list) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return list;
    }

    private static Intent component(String pkg, String cls) {
        Intent i = new Intent();
        i.setComponent(new ComponentName(pkg, cls));
        return i;
    }

    private static boolean tryStart(Context context, Intent intent) {
        try {
            if (intent.resolveActivity(context.getPackageManager()) == null) return false;
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException | IllegalArgumentException e) {
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean openAppDetails(Activity activity) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:" + activity.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(i);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void openNotificationSettings(Activity activity) {
        try {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName());
            activity.startActivity(i);
        } catch (Throwable t) {
            openAppDetails(activity);
        }
    }
}
