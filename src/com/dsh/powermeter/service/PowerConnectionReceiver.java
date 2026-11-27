package com.dsh.powermeter.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.dsh.powermeter.Prefs;
import com.dsh.powermeter.core.ChargeEngine;

/**
 * 插拔电源广播接收器。
 *
 * 作用：插电/拔电时把前台服务拉起来（或让它立即采样一次），保证状态及时刷新。
 * 兼容性：Android 12+ 禁止后台应用启动前台服务，{@link PowerService#start(Context)}
 * 内部已捕获该异常；本类再兜一层 Throwable，任何异常都不让系统崩溃弹框。
 * 不申请任何额外权限，不做网络/文件/shell 操作。
 */
public final class PowerConnectionReceiver extends BroadcastReceiver {

    private static final String TAG = "PowerConnectionReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_POWER_CONNECTED.equals(action)
                && !Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
            return;
        }
        try {
            // 总开关关闭时不去唤起服务，避免通知栏闪一下
            if (!Prefs.get(context).masterEnabled()) return;
        } catch (Throwable t) {
            Log.w(TAG, "读取总开关失败：" + t.getClass().getSimpleName());
        }
        try {
            PowerService.start(context);
        } catch (SecurityException e) {
            Log.w(TAG, "启动服务被拒绝：" + e.getClass().getSimpleName());
        } catch (Throwable t) {
            Log.w(TAG, "插拔电源启动服务失败：" + t.getClass().getSimpleName());
        }
        try {
            // 服务已在运行时可省掉一次等待，立即采样；未运行时该调用是空操作
            ChargeEngine.get(context).sampleNow();
        } catch (Throwable t) {
            Log.w(TAG, "立即采样失败：" + t.getClass().getSimpleName());
        }
    }
}
