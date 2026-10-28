package com.aboumarai.ightanam;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Calendar;

/**
 * Friday reminders, each shown once per Friday as a corner card:
 *  - kahf:    from 9:00 until Maghrib — read / listen to Surat Al-Kahf
 *  - tabkeer: 75 min before Jumu'ah until Dhuhr — go early to the mosque
 *  - dua:     the last hour before Maghrib — the hour of answered du'a
 * If the screen is off or the screensaver is up at the start time, it retries until the window closes.
 */
final class FridayScheduler {
    static final String[] KINDS = {"kahf", "tabkeer", "dua"};
    static final long RETRY_MS = 15 * 60_000;

    /** [start, end] of each reminder on the Friday of {@code day} (any time on that date). */
    static long[][] windows(Context c, Calendar day) {
        SharedPreferences p = AdhanScheduler.prefs(c);
        long[] t = PrayerTimes.forDay(p.getFloat("lat", 24.7136f), p.getFloat("lng", 46.6753f),
                day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH));
        long dhuhr = t[1], maghrib = t[3];
        Calendar nine = (Calendar) day.clone();
        nine.set(Calendar.HOUR_OF_DAY, 9);
        nine.set(Calendar.MINUTE, 0);
        nine.set(Calendar.SECOND, 0);
        nine.set(Calendar.MILLISECOND, 0);
        return new long[][]{
                {nine.getTimeInMillis(), maghrib},
                {dhuhr - 75 * 60_000, dhuhr},
                {maghrib - 60 * 60_000, maghrib},
        };
    }

    static String shownKey(String kind, Calendar day) {
        return "fri_" + kind + "_" + day.get(Calendar.YEAR) + "-" + day.get(Calendar.DAY_OF_YEAR);
    }

    static void markShown(Context c, String kind) {
        AdhanScheduler.prefs(c).edit().putBoolean(shownKey(kind, Calendar.getInstance()), true).apply();
    }

    /**
     * Sets the alarm for the next Friday reminder. A reminder whose window is already open fires
     * after {@code openDelayMs} (a few seconds normally, {@link #RETRY_MS} after a skipped attempt).
     */
    static void schedule(Context c, long openDelayMs) {
        SharedPreferences p = AdhanScheduler.prefs(c);
        long now = System.currentTimeMillis();
        long at = 0;
        String kind = null;

        Calendar day = Calendar.getInstance();
        for (int d = 0; d < 8 && at == 0; d++) {
            if (day.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) {
                long[][] w = windows(c, day);
                for (int i = 0; i < KINDS.length; i++) {
                    if (p.getBoolean(shownKey(KINDS[i], day), false) || now >= w[i][1]) continue;
                    long t = now >= w[i][0] ? Math.min(now + openDelayMs, w[i][1] - 60_000) : w[i][0];
                    if (at == 0 || t < at) { at = t; kind = KINDS[i]; }
                }
            }
            day.add(Calendar.DAY_OF_MONTH, 1);
        }
        if (at == 0) return;

        Intent i = new Intent(c, AdhanReceiver.class).putExtra("friday", kind);
        PendingIntent pi = PendingIntent.getBroadcast(c, 2, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        Log.i(AdhanScheduler.TAG, "next friday reminder " + kind + " " + new java.util.Date(at));
    }

    private FridayScheduler() {}
}
