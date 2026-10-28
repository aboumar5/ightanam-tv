package com.aboumarai.ightanam;

import android.service.dreams.DreamService;
import android.webkit.WebView;

/** Screensaver: shows the adhan clock in big mode; any remote key wakes the TV. */
public class IghtanamDream extends DreamService {
    /** True while the screensaver is on screen; it plays the adhan itself, so the alarm leaves it alone. */
    static volatile boolean active;

    private WebView web;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);
        web = Web.create(this, true);
        setContentView(web);
        AdhanScheduler.schedule(this);
    }

    @Override
    public void onDreamingStarted() { super.onDreamingStarted(); active = true; }

    @Override
    public void onDreamingStopped() {
        active = false;
        FridayScheduler.schedule(this, 5_000);   // show a Friday reminder that was waiting for the screensaver to end
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        active = false;
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDetachedFromWindow();
    }
}
