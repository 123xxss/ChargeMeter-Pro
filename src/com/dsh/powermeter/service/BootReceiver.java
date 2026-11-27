package com.dsh.powermeter.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.dsh.powermeter.Prefs;

/**
 * 开机完成广播接收器。
 *
 * 只在总开关开启时恢复常驻服务；BOOT_COMPLETED 属于系统允许后台启动前台服务的豁免场景，
 * 但仍按防御式写法捕获异常，绝不崩溃。不申请任何额外权限。
 */
public final class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        try {
            if (!Prefs.get(context).masterEnabled()
                    || !Prefs.get(context).notificationEnabled()) {
                Log.i(TAG, "总开关或状态栏关闭，开机不启动监测服务");
                return;
            }
            PowerService.start(context);
        } catch (SecurityException e) {
            Log.w(TAG, "开机启动服务被拒绝：" + e.getClass().getSimpleName());
        } catch (Throwable t) {
            Log.w(TAG, "开机启动服务失败：" + t.getClass().getSimpleName());
        }
    }
}
