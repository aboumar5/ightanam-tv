package com.aboumarai.ightanam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

/** Fires at each prayer time (and for Friday reminders) and shows a card over whatever is on screen. */
public class AdhanReceiver extends BroadcastReceiver {
    /** Skip if the alarm was delayed past the adhan (e.g. the TV was in standby). */
    private static final long MAX_LATE_MS = 3 * 60_000;

    @Override
    public void onReceive(Context c, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            AdhanScheduler.schedule(c);
            FridayScheduler.schedule(c, 30_000);
            return;
        }

        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        boolean test = intent.getBooleanExtra("test", false);
        boolean canShow = pm.isInteractive() && !IghtanamDream.active;

        String friday = intent.getStringExtra("friday");
        if (friday != null) {
            if (canShow || test) {
                if (!test) FridayScheduler.markShown(c, friday);
                c.startForegroundService(new Intent(c, AdhanService.class).putExtra("kind", friday));
            } else {
                Log.i(AdhanScheduler.TAG, "friday " + friday + " deferred: screen off / screensaver");
            }
            if (!test) FridayScheduler.schedule(c, canShow ? 5_000 : FridayScheduler.RETRY_MS);
            return;
        }

        long prayerAt = intent.getLongExtra("prayerAt", 0);
        String key = intent.getStringExtra("key");
        if (key == null) key = "asr";
        if (!test) AdhanScheduler.schedule(c);   // queue the following prayer first

        if (!test && System.currentTimeMillis() - prayerAt > MAX_LATE_MS) { Log.i(AdhanScheduler.TAG, "skip: late"); return; }
        if (!pm.isInteractive()) { Log.i(AdhanScheduler.TAG, "skip: screen off"); return; }
        if (IghtanamDream.active || MainActivity.visible) { Log.i(AdhanScheduler.TAG, "skip: clock already showing"); return; }

        c.startForegroundService(new Intent(c, AdhanService.class).putExtra("kind", "adhan").putExtra("key", key));
    }
}
