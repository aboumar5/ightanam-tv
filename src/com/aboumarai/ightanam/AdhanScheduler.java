package com.aboumarai.ightanam;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Calendar;

/** Keeps exactly one alarm set: at the next prayer time. */
final class AdhanScheduler {
    static final String TAG = "Ightanam";
    static final String PREFS = "ightanam";

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** From the page: city, muezzin id, and exact times as "fajr:<ms>,dhuhr:<ms>,...". */
    static void sync(Context c, double lat, double lng, String muezzin, String times) {
        SharedPreferences p = prefs(c);
        boolean changed = !times.equals(p.getString("times", ""));
        p.edit().putFloat("lat", (float) lat).putFloat("lng", (float) lng)
                .putString("muezzin", muezzin).putString("times", times).apply();
        if (changed) schedule(c);
    }

    static void schedule(Context c) {
        SharedPreferences p = prefs(c);
        long now = System.currentTimeMillis();
        long at = 0;
        String key = null;

        // Exact times from the page, when we have fresh ones.
        for (String item : p.getString("times", "").split(",")) {
            int i = item.indexOf(':');
            if (i < 0) continue;
            long t = Long.parseLong(item.substring(i + 1));
            if (t > now && (at == 0 || t < at)) { at = t; key = item.substring(0, i); }
        }
        // Otherwise compute them here (within about a minute of the page).
        if (at == 0) {
            double lat = p.getFloat("lat", 24.7136f);   // الرياض، نفس افتراضي الصفحة
            double lng = p.getFloat("lng", 46.6753f);
            Calendar day = Calendar.getInstance();
            for (int d = 0; d < 3 && at == 0; d++) {
                long[] t = PrayerTimes.forDay(lat, lng,
                        day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH));
                for (int i = 0; i < t.length; i++) {
                    if (t[i] > now) { at = t[i]; key = PrayerTimes.KEYS[i]; break; }
                }
                day.add(Calendar.DAY_OF_MONTH, 1);
            }
        }

        Intent i = new Intent(c, AdhanReceiver.class).putExtra("prayerAt", at).putExtra("key", key);
        PendingIntent pi = PendingIntent.getBroadcast(c, 1, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        Log.i(TAG, "next adhan " + key + " " + new java.util.Date(at));
    }

    private AdhanScheduler() {}
}
