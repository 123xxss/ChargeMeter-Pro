package com.dsh.powermeter.core;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** 本地充电历史（SQLite，最多 500 条；不联网、不导出）。 */
public final class HistoryDb extends SQLiteOpenHelper {

    private static final String DB_NAME = "charging_history.db";
    private static final int DB_VERSION = 1;
    private static final int MAX_ROWS = 500;

    public static final class Record {
        public long endTimeMs;
        public long durationMs;
        public double avgW;
        public double peakW;
        public int startLevel = -1;
        public int endLevel = -1;
    }

    private static HistoryDb instance;

    public HistoryDb(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    public static synchronized HistoryDb get(Context context) {
        if (instance == null) {
            instance = new HistoryDb(context.getApplicationContext());
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sessions ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "end_ms INTEGER NOT NULL,"
                + "duration_ms INTEGER NOT NULL,"
                + "avg_w REAL NOT NULL,"
                + "peak_w REAL NOT NULL,"
                + "start_level INTEGER NOT NULL,"
                + "end_level INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // v1；未来升级时只做增量迁移。当前保持数据。
    }

    public void add(SessionTracker.Summary s) {
        if (s == null || s.durationMs < 30_000L) return;
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues v = new ContentValues();
            v.put("end_ms", s.endTimeMs);
            v.put("duration_ms", s.durationMs);
            v.put("avg_w", s.avgW);
            v.put("peak_w", s.peakW);
            v.put("start_level", s.startLevel);
            v.put("end_level", s.endLevel);
            db.insert("sessions", null, v);
            db.execSQL("DELETE FROM sessions WHERE id NOT IN "
                    + "(SELECT id FROM sessions ORDER BY id DESC LIMIT " + MAX_ROWS + ")");
        } catch (Throwable ignored) {
        }
    }

    public List<Record> latest(int limit) {
        List<Record> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("sessions",
                new String[]{"end_ms", "duration_ms", "avg_w", "peak_w",
                        "start_level", "end_level"},
                null, null, null, null, "id DESC", String.valueOf(Math.max(1, limit)))) {
            while (c.moveToNext()) {
                Record r = new Record();
                r.endTimeMs = c.getLong(0);
                r.durationMs = c.getLong(1);
                r.avgW = c.getDouble(2);
                r.peakW = c.getDouble(3);
                r.startLevel = c.getInt(4);
                r.endLevel = c.getInt(5);
                out.add(r);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public int count() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM sessions", null)) {
            if (c.moveToFirst()) return c.getInt(0);
        } catch (Throwable ignored) {
        }
        return 0;
    }

    public void clear() {
        try {
            getWritableDatabase().delete("sessions", null, null);
        } catch (Throwable ignored) {
        }
    }
}
