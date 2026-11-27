package com.dsh.powermeter;

import android.app.Application;

/**
 * 应用入口。保持极简：不做任何网络、广告、统计分析初始化。
 */
public final class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // 提前初始化设置单例（只读 SharedPreferences，不涉及 I/O 密集操作）。
        Prefs.get(this);
    }
}
