package com.aboumarai.ightanam;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Kids mode: the home row shows only the apps a parent picked, everything else is hidden, and
 * watching time is limited per day. Time counts from opening an app on the home screen until
 * coming back to it; when the day's time runs out, an alarm brings the home screen back over the
 * app with a "time's up" screen. A parent's 4-digit PIN turns it off or adds time.
 */
final class KidsMode {
    static final String EXTRA_TIME_UP = "kidsTimeUp";

    private static SharedPreferences p(Context c) { return AdhanScheduler.prefs(c); }

    static boolean on(Context c) { return p(c).getBoolean("kids_on", false); }

    static List<String> apps(Context c) {
        List<String> out = new ArrayList<>();
        for (String s : p(c).getString("kids_apps", "").split(",")) if (!s.isEmpty()) out.add(s);
        return out;
    }

    static void setApps(Context c, List<String> apps) {
        p(c).edit().putString("kids_apps", String.join(",", apps)).apply();
    }

    /** Daily limit in minutes; 0 means no limit. */
    static int limit(Context c) { return p(c).getInt("kids_limit", 60); }

    static void setLimit(Context c, int minutes) { p(c).edit().putInt("kids_limit", minutes).apply(); }

    static String pin(Context c) { return p(c).getString("kids_pin", null); }

    static void setPin(Context c, String pin) { p(c).edit().putString("kids_pin", pin).apply(); }

    static void setOn(Context c, boolean on) {
        if (!on) endSession(c);
        p(c).edit().putBoolean("kids_on", on).apply();
    }

    private static String today() {
        Calendar d = Calendar.getInstance();
        return d.get(Calendar.YEAR) + "-" + d.get(Calendar.DAY_OF_YEAR);
    }

    /** Time watched today, not counting a session still running. */
    private static long usedToday(Context c) {
        return today().equals(p(c).getString("kids_day", "")) ? p(c).getLong("kids_used", 0) : 0;
    }

    private static void setUsed(Context c, long ms) {
        p(c).edit().putString("kids_day", today()).putLong("kids_used", Math.max(0, ms)).apply();
    }

    /** Time left today in ms, counting a running session; Long.MAX_VALUE without a limit. */
    static long remaining(Context c) {
        int limit = limit(c);
        if (limit <= 0) return Long.MAX_VALUE;
        long start = p(c).getLong("kids_session", 0);
        long running = start > 0 ? System.currentTimeMillis() - start : 0;
        return Math.max(0, limit * 60_000L - usedToday(c) - running);
    }

    static boolean timeUp(Context c) { return on(c) && remaining(c) <= 0; }

    /** An app was opened from the home screen: start counting and set the time-up alarm. */
    static void startSession(Context c) {
        if (!on(c) || limit(c) <= 0) return;
        long now = System.currentTimeMillis();
        long left = remaining(c);
        p(c).edit().putLong("kids_session", now).apply();
        alarm(c).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, now + left, pending(c));
    }

    /** Back on the home screen (or time ran out): add the session to today's total. */
    static void endSession(Context c) {
        long start = p(c).getLong("kids_session", 0);
        if (start <= 0) return;
        String day = p(c).getString("kids_day", "");
        long used = today().equals(day) ? p(c).getLong("kids_used", 0) : 0;
        // A session that began yesterday only counts from midnight.
        Calendar midnight = Calendar.getInstance();
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 0);
        long from = Math.max(start, midnight.getTimeInMillis());
        p(c).edit().putLong("kids_session", 0).apply();
        setUsed(c, used + Math.max(0, System.currentTimeMillis() - from));
        alarm(c).cancel(pending(c));
    }

    /** Parent's "+30 minutes" on the time's-up screen: that much time left from now on. */
    static void addMinutes(Context c, int minutes) {
        long used = Math.min(usedToday(c), limit(c) * 60_000L);   // overtime doesn't eat into it
        setUsed(c, used - minutes * 60_000L);
    }

    /** For testing: today's time is all used up. */
    static void useUpToday(Context c) { setUsed(c, limit(c) * 60_000L); }

    private static AlarmManager alarm(Context c) {
        return (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
    }

    private static PendingIntent pending(Context c) {
        return PendingIntent.getBroadcast(c, 3, new Intent(c, TimeUpReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Fires when the day's watching time runs out: bring the home screen back over the app. */
    public static class TimeUpReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent intent) {
            if (!on(c)) return;
            endSession(c);
            // Allowed from the background because the app may draw over other apps.
            c.startActivity(new Intent(c, HomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_TIME_UP, true));
        }
    }

    private KidsMode() {}
}
