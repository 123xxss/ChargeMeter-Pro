package com.dsh.powermeter.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.dsh.powermeter.Prefs;
import com.dsh.powermeter.core.ChargeEngine;
import com.dsh.powermeter.core.ChargeState;
import com.dsh.powermeter.core.PowerSnapshot;
import com.dsh.powermeter.core.StateStore;

/**
 * 常驻前台服务：持有采样引擎，并把最新快照渲染成低优先级通知栏卡片。
 *
 * 生命周期要点：
 * - onCreate 先建渠道并 startForeground 展示待机通知，再启动引擎/订阅快照，
 *   保证在系统 5 秒时限内进入前台，避免 ANR 与被杀。
 * - 总开关关闭（快照 masterEnabled=false 或设置里已关闭）时立即 stopForeground + stopSelf。
 * - 通知权限被撤销时只降级为「不刷新通知」，绝不崩溃。
 * - 不申请网络、不写文件、不执行 shell、不做跨进程通信。
 */
public final class PowerService extends Service implements StateStore.Listener {

    private static final String TAG = "PowerService";

    private Prefs prefs;
    private ChargeEngine engine;

    /** 是否已成功进入前台。 */
    private boolean foreground;
    /** 是否正在停止：防止重复释放 / 重复刷新。 */
    private boolean stopping;
    /** 通知文本去重键，避免每秒无意义刷新。 */
    private String lastKey;
    /** 仅用于状态切换日志。 */
    private ChargeState lastState;
    private final Handler watchdogHandler = new Handler(Looper.getMainLooper());
    private long lastSnapshotAt;
    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            if (stopping) return;
            try {
                long now = System.currentTimeMillis();
                if (foreground && engine != null && now - lastSnapshotAt > 4000L) {
                    // 后台定时器被系统/ROM 打断时，服务侧兜底再触发一次采样。
                    Log.w(TAG, "后台采样超时，watchdog 重新采样");
                    engine.sampleNow();
                }
            } catch (Throwable ignored) {
            }
            watchdogHandler.postDelayed(this, 5000L);
        }
    };

    // ------------------------------------------------------------ 静态入口

    /** 启动服务；Android 12+ 后台启动前台服务受限时只记日志，不崩溃。 */
    public static void start(Context context) {
        if (context == null) return;
        try {
            context.startForegroundService(new Intent(context, PowerService.class));
        } catch (SecurityException e) {
            Log.w(TAG, "启动前台服务被拒绝：" + e.getClass().getSimpleName());
        } catch (Throwable t) {
            // Android 12+ 的 ForegroundServiceStartNotAllowedException 走这里（不直接 catch 该类，
            // 该类在 API 31 才存在，按类型捕获会在低版本引发校验问题）
            Log.w(TAG, "启动前台服务失败：" + t.getClass().getSimpleName());
        }
    }

    /**
     * 从可见界面启动服务：使用 startService 而不是 startForegroundService。
     * 这样即使进入前台失败，也不会触发系统
     * "Context.startForegroundService() did not then call Service.startForeground()" 的进程级崩溃。
     */
    public static void startFromActivity(Context context) {
        if (context == null) return;
        try {
            context.startService(new Intent(context, PowerService.class));
        } catch (Throwable t) {
            // 可见界面下 startService 失败通常意味着系统/OEM 限制了服务；
            // 不再改走 startForegroundService，避免触发进程级 RemoteServiceException。
            Log.w(TAG, "从界面启动服务失败：" + t.getClass().getSimpleName());
        }
    }

    /** 停止服务。 */
    public static void stop(Context context) {
        if (context == null) return;
        try {
            context.stopService(new Intent(context, PowerService.class));
        } catch (Throwable t) {
            Log.w(TAG, "停止前台服务失败：" + t.getClass().getSimpleName());
        }
    }

    // ------------------------------------------------------------ 生命周期

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = Prefs.get(this);

        // 1) 先建渠道 + 立刻进入前台（重活之后再做）
        NotificationHelper.ensureChannel(this);
        NotificationHelper.resetArtifacts(this);
        // 先只用最基础的字段进入前台：避免自定义 action / 图标加载失败导致
        // startForeground 失败，进而触发系统级 RemoteServiceException 闪退。
        boolean ok = false;
        try {
            ok = enterForeground(NotificationHelper.buildMinimal(this));
        } catch (Throwable t) {
            Log.w(TAG, "构建最小前台通知失败：" + t.getClass().getSimpleName());
        }
        if (!ok) {
            Log.w(TAG, "进入前台失败，停止服务（界面仍可用）");
            stopSelf();
            return;
        }
        lastKey = null;

        // 2) 再启动采样引擎并订阅快照
        try {
            engine = ChargeEngine.get(this);
            engine.retain();
            StateStore.get().addListener(this);
            lastSnapshotAt = System.currentTimeMillis();
            watchdogHandler.removeCallbacks(watchdog);
            watchdogHandler.postDelayed(watchdog, 5000L);
        } catch (Throwable t) {
            Log.w(TAG, "启动采样引擎失败：" + t.getClass().getSimpleName());
        }

        if (!prefs.masterEnabled()) {
            shutdown();
            return;
        }
        // 有上一帧快照时立即渲染，避免等待下一次采样
        refresh(StateStore.get().latest());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (prefs != null && !prefs.masterEnabled()) {
                shutdown();
                return START_NOT_STICKY;
            }
            refresh(StateStore.get().latest());
        } catch (Throwable t) {
            Log.w(TAG, "处理启动命令失败：" + t.getClass().getSimpleName());
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopping = true;
        watchdogHandler.removeCallbacksAndMessages(null);
        StateStore.get().removeListener(this);
        if (foreground) {
            try {
                stopForeground(Service.STOP_FOREGROUND_REMOVE);
            } catch (Throwable ignored) {
            }
            foreground = false;
        }
        if (engine != null) {
            try {
                engine.release();
            } catch (Throwable t) {
                Log.w(TAG, "释放采样引擎失败：" + t.getClass().getSimpleName());
            }
            engine = null;
        }
        Log.i(TAG, "服务已停止");
        super.onDestroy();
    }

    // ------------------------------------------------------------ 快照回调

    @Override
    public void onSnapshot(PowerSnapshot snapshot) {
        lastSnapshotAt = System.currentTimeMillis();
        refresh(snapshot);
    }

    /** 把快照渲染到通知栏；异常一律吞掉，保证主线程不崩。 */
    private void refresh(PowerSnapshot snapshot) {
        try {
            if (stopping || snapshot == null) return;
            if (!snapshot.masterEnabled || !prefs.masterEnabled()) {
                shutdown();
                return;
            }
            if (!prefs.notificationEnabled()) {
                // 状态栏关闭后不能保留前台通知：直接停止服务，主界面仍可手动采样。
                shutdown();
                return;
            }
            if (lastState != snapshot.state) {
                lastState = snapshot.state;
                Log.i(TAG, "状态切换：" + lastState);
            }
            NotificationHelper.Content content = NotificationHelper.content(prefs, snapshot);
            if (snapshot.sessionActive && snapshot.sessionDurationMs > 0) {
                content.chronometerBase = System.currentTimeMillis() - snapshot.sessionDurationMs;
            }
            // 通知栏必须与主界面保持同一帧：不再用文本去重拦掉刷新。
            // 每秒 notify 一次成本很低，却能避免二级文本/系统缓存导致的“慢一拍”。
            lastKey = content.key();
            post(content);
        } catch (Throwable t) {
            Log.w(TAG, "刷新通知失败：" + t.getClass().getSimpleName());
        }
    }

    private void post(NotificationHelper.Content content) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            nm.notify(NotificationHelper.NOTIFICATION_ID, NotificationHelper.build(this, content));
        } catch (SecurityException e) {
            // 通知权限被撤销：跳过本次刷新即可，服务继续运行
            Log.w(TAG, "通知权限被拒绝，跳过刷新");
        } catch (Throwable t) {
            Log.w(TAG, "通知刷新失败：" + t.getClass().getSimpleName());
        }
    }

    // ------------------------------------------------------------ 内部工具

    /** 进入前台；通知权限异常时降级重试一次，仍失败则返回 false 由调用方 stopSelf。 */
    private boolean enterForeground(Notification notification) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NotificationHelper.NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NotificationHelper.NOTIFICATION_ID, notification);
            }
            foreground = true;
            return true;
        } catch (SecurityException e) {
            Log.w(TAG, "startForeground 被拒绝：" + e.getClass().getSimpleName());
            try {
                startForeground(NotificationHelper.NOTIFICATION_ID, notification);
                foreground = true;
                return true;
            } catch (Throwable t) {
                Log.w(TAG, "startForeground 重试失败：" + t.getClass().getSimpleName());
                return false;
            }
        } catch (Throwable t) {
            Log.w(TAG, "startForeground 失败：" + t.getClass().getSimpleName());
            return false;
        }
    }

    /** 总开关关闭：撤下通知并停止自身。 */
    private void shutdown() {
        if (stopping) return;
        stopping = true;
        watchdogHandler.removeCallbacksAndMessages(null);
        Log.i(TAG, "总开关已关闭，停止服务");
        StateStore.get().removeListener(this);
        if (foreground) {
            try {
                stopForeground(Service.STOP_FOREGROUND_REMOVE);
            } catch (Throwable ignored) {
            }
            foreground = false;
        }
        stopSelf();
    }
}
