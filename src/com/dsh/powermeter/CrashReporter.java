package com.dsh.powermeter;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 极简崩溃记录器。
 *
 * 目的：真机上遇到启动闪退时，下一次打开 App 可以把堆栈展示出来并一键复制，
 * 不依赖 adb、不上传任何数据、不需要网络权限。
 *
 * 安全：只写应用私有目录；内容仅异常类型/堆栈/系统版本，不含电流电压原始序列，
 * 不含设备唯一标识、账号或位置。
 */
public final class CrashReporter {

    private static final String TAG = "PowerMeterCrash";
    private static final String FILE = "last_crash.txt";
    private static final int MAX_BYTES = 96 * 1024;

    private CrashReporter() {
    }

    public static void install(Context context) {
        final Context app = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            record(app, error);
            try {
                Log.e(TAG, "uncaught on " + thread.getName() + ":\n" + stack(error));
            } catch (Throwable ignored) {
            }
            if (previous != null) {
                previous.uncaughtException(thread, error);
            } else {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        });
    }

    /** 记录一次异常（崩溃处理器与安全模式 UI 共用）。 */
    public static void record(Context context, Throwable error) {
        if (context == null || error == null) return;
        try (FileOutputStream out = new FileOutputStream(
                new File(context.getApplicationContext().getFilesDir(), FILE))) {
            out.write(buildReport(error).getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (Throwable ignored) {
        }
    }

    /** 读取上次启动的异常报告；没有返回 null。 */
    public static String readLast(Context context) {
        if (context == null) return null;
        File f = new File(context.getApplicationContext().getFilesDir(), FILE);
        if (!f.isFile() || f.length() <= 0) return null;
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            int total = 0;
            while ((n = in.read(buffer)) > 0 && total < MAX_BYTES) {
                out.write(buffer, 0, n);
                total += n;
            }
            String text = out.toString("UTF-8");
            return text == null || text.trim().isEmpty() ? null : text;
        } catch (Throwable t) {
            return null;
        }
    }

    public static void clear(Context context) {
        try {
            File f = new File(context.getApplicationContext().getFilesDir(), FILE);
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        } catch (Throwable ignored) {
        }
    }

    private static String buildReport(Throwable error) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("PowerMeter Pro 启动异常报告");
        pw.println("时间: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(new Date()));
        pw.println("设备: " + Build.MANUFACTURER + " " + Build.BRAND + " " + Build.MODEL);
        pw.println("系统: Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
        pw.println("应用: 2.1.0");
        pw.println("线程: " + Thread.currentThread().getName());
        pw.println("----");
        pw.print(stack(error));
        pw.flush();
        String s = sw.toString();
        if (s.length() > MAX_BYTES / 2) s = s.substring(0, MAX_BYTES / 2);
        return s;
    }

    public static String stack(Throwable error) {
        if (error == null) return "";
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            error.printStackTrace(pw);
            pw.flush();
            return sw.toString();
        } catch (Throwable t) {
            return String.valueOf(error);
        }
    }
}
