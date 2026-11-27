package com.dsh.powermeter.core;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 进程内状态分发。引擎在主线程发布快照，UI 与前台服务订阅。
 * 不做跨进程通信，不暴露任何 Binder/Provider。
 */
public final class StateStore {

    public interface Listener {
        void onSnapshot(PowerSnapshot snapshot);
    }

    private static final StateStore INSTANCE = new StateStore();

    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile PowerSnapshot latest;

    private StateStore() {
    }

    public static StateStore get() {
        return INSTANCE;
    }

    public void publish(PowerSnapshot snapshot) {
        latest = snapshot;
        for (Listener l : listeners) {
            try {
                l.onSnapshot(snapshot);
            } catch (Throwable ignored) {
            }
        }
    }

    public PowerSnapshot latest() {
        return latest;
    }

    public void addListener(Listener listener) {
        if (listener == null || listeners.contains(listener)) return;
        listeners.add(listener);
        PowerSnapshot s = latest;
        if (s != null) {
            main.post(() -> {
                try {
                    listener.onSnapshot(s);
                } catch (Throwable ignored) {
                }
            });
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }
}
