package com.dsh.powermeter.shizuku;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.dsh.powermeter.Prefs;

import rikka.shizuku.Shizuku;

/**
 * Shizuku 只读增强读取客户端。
 *
 * - 未安装 / 未授权 / 服务被杀：全部 fail-closed，返回 null，调用方回退标准 BatteryManager；
 * - 只绑定本应用内、只读标准 sysfs 节点的 UserService；
 * - 不做 shell、不做写操作、不申请超出 Shizuku 基本授权之外的能力。
 */
public final class ShizukuBatterySource {

    private static final String TAG = "ShizukuBattery";
    private static final int REQUEST_CODE = 10086;
    private static final int USER_SERVICE_VERSION = 1;

    private static Context appContext;
    private static volatile boolean initialized;
    private static volatile boolean connected;
    private static volatile IBatteryUserService service;
    private static volatile Runnable permissionListener;

    private static final ServiceConnection CONNECTION = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            try {
                service = IBatteryUserService.Stub.asInterface(binder);
                connected = service != null;
                Log.i(TAG, "Shizuku UserService 已连接");
            } catch (Throwable t) {
                connected = false;
                service = null;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            connected = false;
            service = null;
            Log.w(TAG, "Shizuku UserService 断开，回退标准路径");
        }
    };

    private static final Shizuku.OnRequestPermissionResultListener PERMISSION_LISTENER =
            (requestCode, grantResult) -> {
                if (requestCode != REQUEST_CODE) return;
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    bindIfPossible();
                }
                Runnable r = permissionListener;
                if (r != null) r.run();
            };

    private static final Shizuku.OnBinderDeadListener DEAD_LISTENER = () -> {
        connected = false;
        service = null;
        Log.w(TAG, "Shizuku binder 已断开，回退标准路径");
    };

    private ShizukuBatterySource() {
    }

    public static void init(Context context) {
        if (initialized || context == null) return;
        synchronized (ShizukuBatterySource.class) {
            if (initialized) return;
            appContext = context.getApplicationContext();
            try {
                Shizuku.addRequestPermissionResultListener(PERMISSION_LISTENER);
                Shizuku.addBinderDeadListener(DEAD_LISTENER);
                Shizuku.addBinderReceivedListenerSticky(() -> {
                    if (Prefs.get(appContext).shizukuReadEnabled()
                            && hasPermission()) {
                        bindIfPossible();
                    }
                });
            } catch (Throwable t) {
                Log.w(TAG, "初始化 Shizuku 监听失败：" + t.getClass().getSimpleName());
            }
            initialized = true;
        }
    }

    public static void setPermissionListener(Runnable listener) {
        permissionListener = listener;
    }

    public static boolean isAvailable() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean hasPermission() {
        try {
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void requestPermission(Activity activity) {
        if (activity == null) return;
        try {
            if (!isAvailable()) return;
            if (hasPermission()) {
                bindIfPossible();
            } else {
                Shizuku.requestPermission(REQUEST_CODE);
            }
        } catch (Throwable t) {
            Log.w(TAG, "请求 Shizuku 权限失败：" + t.getClass().getSimpleName());
        }
    }

    public static void bindIfPossible() {
        if (appContext == null) return;
        if (!isAvailable() || !hasPermission()) return;
        try {
            Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
                    new ComponentName(appContext, BatteryUserService.class))
                    .daemon(false)
                    .processNameSuffix("battery")
                    .tag("powermeter-battery")
                    .version(USER_SERVICE_VERSION)
                    .debuggable(false);
            Shizuku.bindUserService(args, CONNECTION);
        } catch (Throwable t) {
            connected = false;
            service = null;
            Log.w(TAG, "绑定 Shizuku UserService 失败：" + t.getClass().getSimpleName());
        }
    }

    public static void unbind() {
        connected = false;
        service = null;
        if (appContext == null) return;
        try {
            Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
                    new ComponentName(appContext, BatteryUserService.class))
                    .tag("powermeter-battery")
                    .version(USER_SERVICE_VERSION);
            Shizuku.unbindUserService(args, CONNECTION, true);
        } catch (Throwable ignored) {
        }
    }

    /** 读取系统温度/GPU（只读 sysfs）；不可用返回 null。 */
    public static SystemValues readSystem() {
        if (!connected || service == null) return null;
        try {
            String raw = service.readSystem();
            if (raw == null || raw.isEmpty()) return null;
            String[] p = raw.split(";", -1);
            if (p.length < 2) return null;
            SystemValues v = new SystemValues();
            v.thermalC = num(p[0]);
            v.gpuPercent = num(p[1]);
            return v;
        } catch (Throwable t) {
            connected = false;
            service = null;
            return null;
        }
    }

    /** 读取一次；不可用/异常返回 null（调用方必须回退标准路径）。 */
    public static Values read() {
        if (!connected || service == null) return null;
        try {
            String raw = service.readBattery();
            if (raw == null || raw.isEmpty()) return null;
            String[] parts = raw.split(";", -1);
            if (parts.length < 5) return null;
            Values v = new Values();
            double rawV = num(parts[0]);
            if (rawV > 100_000) v.voltageV = rawV / 1_000_000.0;      // 标准 µV
            else if (rawV > 1_000) v.voltageV = rawV / 1_000.0;      // mV
            else v.voltageV = rawV;                                  // V
            double rawA = num(parts[1]);
            if (Math.abs(rawA) > 30_000_000) v.currentA = rawA / 1_000.0; // mA
            else v.currentA = rawA / 1_000_000.0;                    // 标准 µA
            double rawT = num(parts[2]);
            if (rawT > 2_000) v.tempC = rawT / 1_000.0;
            else if (rawT > 200) v.tempC = rawT / 10.0;
            else v.tempC = rawT;
            double rawCounter = num(parts[3]);
            v.chargeCounterUah = rawCounter > 0 && rawCounter < 100_000_000
                    ? (long) rawCounter : -1;
            v.status = parts[4];
            if (parts.length >= 6) {
                double rawMax = num(parts[5]);
                if (rawMax > 100_000) v.voltageMaxV = rawMax / 1_000_000.0;
                else if (rawMax > 1_000) v.voltageMaxV = rawMax / 1_000.0;
                else if (rawMax > 1) v.voltageMaxV = rawMax;
            }
            if (!(v.voltageV >= 2.0 && v.voltageV <= 10.5)) return null;
            if (!Double.isFinite(v.currentA) || Math.abs(v.currentA) > 40) return null;
            return v;
        } catch (RemoteException | RuntimeException e) {
            connected = false;
            service = null;
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static double num(String s) {
        if (s == null || s.trim().isEmpty()) return Double.NaN;
        try {
            return Double.parseDouble(s.trim());
        } catch (Throwable t) {
            return Double.NaN;
        }
    }

    public static final class SystemValues {
        public double thermalC = Double.NaN;
        public double gpuPercent = Double.NaN;
    }

    public static final class Values {
        public double voltageV = Double.NaN;
        public double currentA = Double.NaN;
        public double tempC = Double.NaN;
        public long chargeCounterUah = -1;
        public String status;
        public double voltageMaxV = Double.NaN;
    }
}
