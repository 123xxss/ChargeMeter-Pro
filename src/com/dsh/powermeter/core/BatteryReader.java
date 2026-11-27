package com.dsh.powermeter.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;

import com.dsh.powermeter.BrandCompat;

/**
 * 电池数据采集：BatteryManager 粘性广播 + 属性读取，可选只读 sysfs 增强。
 *
 * 只在被动事件（广播）与调用方 tick 时读取，不做后台高频轮询。
 * 所有异常都被捕获，读不到就返回“不支持”，由归一化层降级，不崩溃。
 */
public final class BatteryReader {

    private static final String TAG = "BatteryReader";

    private final Context context;
    private final BatteryManager batteryManager;
    private final Handler mainHandler;
    private final IntentFilter batteryFilter;
    private final IntentFilter powerFilter;
    private final IntentFilter screenFilter;

    private Intent lastBatteryIntent;
    private int miuiPowerMaxW;
    private String miuiQuickChargeType;
    private SysfsReader.Values sysfsCache;
    private long sysfsCacheAt;
    private boolean started;
    private Runnable onEvent;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            if (intent == null) return;
            lastBatteryIntent = intent;
            fireEvent();
        }
    };

    private final BroadcastReceiver eventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            fireEvent();
        }
    };

    private final BroadcastReceiver miuiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            if (intent == null) return;
            try {
                int power = intent.getIntExtra(BrandCompat.MIUI_EXTRA_POWER_MAX, 0);
                if (power <= 0) {
                    String s = intent.getStringExtra(BrandCompat.MIUI_EXTRA_POWER_MAX);
                    if (s != null) {
                        power = (int) Double.parseDouble(s);
                    }
                }
                if (power > 0 && power <= 300) {
                    miuiPowerMaxW = power;
                }
                String type = intent.getStringExtra(BrandCompat.MIUI_EXTRA_QUICK_CHARGE_TYPE);
                if (type != null) {
                    miuiQuickChargeType = type;
                }
            } catch (Throwable ignored) {
                // 外部广播按不可信输入处理，解析失败直接忽略
            }
            fireEvent();
        }
    };

    public BatteryReader(Context context) {
        this.context = context.getApplicationContext();
        this.batteryManager = (BatteryManager) this.context.getSystemService(Context.BATTERY_SERVICE);
        this.mainHandler = new Handler(android.os.Looper.getMainLooper());
        this.batteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        this.powerFilter = new IntentFilter();
        this.powerFilter.addAction(Intent.ACTION_POWER_CONNECTED);
        this.powerFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        this.screenFilter = new IntentFilter();
        this.screenFilter.addAction(Intent.ACTION_SCREEN_ON);
        this.screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
    }

    public void start(Runnable onEvent) {
        if (started) {
            this.onEvent = onEvent;
            return;
        }
        this.onEvent = onEvent;
        started = true;
        try {
            register(batteryReceiver, batteryFilter, false);
            register(eventReceiver, powerFilter, false);
            register(eventReceiver, screenFilter, false);
            if (BrandCompat.brand() == BrandCompat.Brand.XIAOMI) {
                IntentFilter f = new IntentFilter(BrandCompat.MIUI_QUICK_CHARGE_ACTION);
                try {
                    register(miuiReceiver, f, true);
                } catch (Throwable t) {
                    register(miuiReceiver, f, false);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "注册电池监听失败", t);
        }
        fireEvent();
    }

    public void stop() {
        if (!started) return;
        started = false;
        try {
            context.unregisterReceiver(batteryReceiver);
        } catch (Throwable ignored) {
        }
        try {
            context.unregisterReceiver(eventReceiver);
        } catch (Throwable ignored) {
        }
        try {
            context.unregisterReceiver(miuiReceiver);
        } catch (Throwable ignored) {
        }
    }

    private void register(BroadcastReceiver receiver, IntentFilter filter, boolean exported) {
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter,
                    exported ? Context.RECEIVER_EXPORTED : Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(receiver, filter);
        }
    }

    private void fireEvent() {
        Runnable r = onEvent;
        if (r != null) {
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                r.run();
            } else {
                mainHandler.post(r);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private Intent stickyIntent() {
        if (lastBatteryIntent != null) return lastBatteryIntent;
        try {
            lastBatteryIntent = context.registerReceiver(null, batteryFilter);
        } catch (Throwable ignored) {
        }
        return lastBatteryIntent;
    }

    /** @param enhanced 是否尝试只读 sysfs 增强路径；@param shizuku 是否尝试 Shizuku 只读路径 */
    public RawBattery read(boolean enhanced, boolean shizuku) {
        Intent intent = stickyIntent();
        RawBattery.Builder b = RawBattery.builder().timestampMs(System.currentTimeMillis());

        int status = BatteryManager.BATTERY_STATUS_UNKNOWN;
        int plugged = 0;
        int level = -1;
        int voltageMv = -1;
        int tempDeci = Integer.MIN_VALUE;

        if (intent != null) {
            status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
            plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            int rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (rawLevel >= 0 && scale > 0) {
                level = Math.round(rawLevel * 100f / scale);
            }
            voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
            tempDeci = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            if (level < 0) {
                level = intent.getIntExtra("level", -1);
            }
        }
        if (level < 0) {
            try {
                level = batteryManager == null ? -1
                        : batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            } catch (Throwable ignored) {
            }
        }

        b.status(status).plugged(plugged).level(level)
                .voltageMv(voltageMv)
                .temperatureDeciC(tempDeci);

        // 电流：BatteryManager 标准属性
        boolean currentSupported = false;
        long currentRaw = 0;
        if (batteryManager != null) {
            try {
                long v = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                if (v != Long.MIN_VALUE) {
                    currentRaw = v;
                    currentSupported = true;
                }
            } catch (Throwable ignored) {
            }
            if (!currentSupported) {
                try {
                    int v = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                    if (v != Integer.MIN_VALUE) {
                        currentRaw = v;
                        currentSupported = true;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        b.currentRaw(currentRaw).currentSupported(currentSupported);

        long counter = -1;
        if (batteryManager != null) {
            try {
                long c = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
                if (c != Long.MIN_VALUE && c > 0) counter = c;
            } catch (Throwable ignored) {
            }
        }
        b.chargeCounterUah(counter);

        int powerMaxW = 0;
        if (miuiPowerMaxW > 0) {
            powerMaxW = miuiPowerMaxW;
        } else if (intent != null) {
            powerMaxW = powerMaxFromBatteryIntent(intent);
        }
        b.powerMaxW(powerMaxW).quickChargeType(miuiQuickChargeType);

        // 增强读取（只读标准 sysfs 节点）
        if (enhanced) {
            SysfsReader.Values v = readSysfsCached();
            if (v != null) {
                b.sysfsVoltageV(v.voltageV)
                        .sysfsCurrentA(v.currentA)
                        .sysfsTempC(v.tempC)
                        .sysfsStatus(v.status);
                if (counter < 0 && v.chargeCounterUah > 0) {
                    b.chargeCounterUah(v.chargeCounterUah);
                }
            }
        }
        // Shizuku 只读 UserService（实验性）：不可用时保持标准路径
        if (shizuku) {
            try {
                com.dsh.powermeter.shizuku.ShizukuBatterySource.Values v =
                        com.dsh.powermeter.shizuku.ShizukuBatterySource.read();
                if (v != null) {
                    b.sysfsVoltageV(v.voltageV)
                            .sysfsCurrentA(v.currentA)
                            .sysfsTempC(v.tempC)
                            .sysfsStatus(v.status);
                    if (counter < 0 && v.chargeCounterUah > 0) {
                        b.chargeCounterUah(v.chargeCounterUah);
                    }
                }
            } catch (Throwable ignored) {
                // fail-closed：Shizuku 不可用时完全走标准路径
            }
        }
        return b.build();
    }

    private SysfsReader.Values readSysfsCached() {
        long now = System.currentTimeMillis();
        if (sysfsCache != null && now - sysfsCacheAt < 800) {
            return sysfsCache;
        }
        try {
            sysfsCache = SysfsReader.read();
            sysfsCacheAt = now;
        } catch (Throwable ignored) {
            sysfsCache = null;
        }
        return sysfsCache;
    }

    /**
     * 系统电池广播里可能带有 max_charging_current / max_charging_voltage。
     * 这些字段不是公开 API，按不可信输入校验后再使用，避免虚假高功率。
     */
    private int powerMaxFromBatteryIntent(Intent intent) {
        try {
            int maxCurrent = intent.getIntExtra("max_charging_current", -1);
            int maxVoltage = intent.getIntExtra("max_charging_voltage", -1);
            if (maxCurrent <= 0 || maxVoltage <= 0) return 0;
            double w1 = maxCurrent / 1_000_000.0 * (maxVoltage / 1000.0);
            if (w1 >= 5 && w1 <= 300) return (int) Math.round(w1);
            double w2 = maxCurrent / 1000.0 * (maxVoltage / 1000.0);
            if (w2 >= 5 && w2 <= 300) return (int) Math.round(w2);
            double w3 = maxCurrent / 1000.0 * maxVoltage;
            if (w3 >= 5 && w3 <= 300) return (int) Math.round(w3);
        } catch (Throwable ignored) {
        }
        return 0;
    }

    public boolean screenOn() {
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isInteractive();
        } catch (Throwable t) {
            return true;
        }
    }

    public boolean batteryBroadcastAvailable() {
        return stickyIntent() != null;
    }
}
