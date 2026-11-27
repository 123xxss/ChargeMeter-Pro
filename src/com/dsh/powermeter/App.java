package com.dsh.powermeter;

import android.app.Application;

/**
 * 应用入口。保持极简：不做任何网络、广告、统计分析初始化。
 */
public final class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // 先装崩溃记录器，保证启动早期异常也能在下次打开时看到堆栈。
        try {
            CrashReporter.install(this);
        } catch (Throwable ignored) {
        }
        // 提前初始化设置单例；个别 ROM 在极早期 SharedPreferences 不可用时也不应闪退。
        try {
            Prefs.get(this);
        } catch (Throwable t) {
            CrashReporter.record(this, t);
        }
        // 清掉上一版本遗留的旧通知/旧图标渠道；新服务会重新发布新图标通知。
        try {
            com.dsh.powermeter.service.NotificationHelper.cleanupLegacy(this);
        } catch (Throwable t) {
            CrashReporter.record(this, t);
        }
        // Shizuku 只读增强读取：初始化监听但不主动请求权限。
        try {
            com.dsh.powermeter.shizuku.ShizukuBatterySource.init(this);
        } catch (Throwable t) {
            CrashReporter.record(this, t);
        }
    }
}
