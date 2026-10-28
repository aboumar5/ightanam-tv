package com.aboumarai.ightanam;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.webkit.WebView;

/** Opens the clock as a normal app; settings saved here are shared with the screensaver and adhan card. */
public class MainActivity extends Activity {
    /** True while on screen; the page plays the adhan itself then, so the card stays away. */
    static volatile boolean visible;
    /** Surah index to start playing on open (e.g. Al-Kahf from the Friday card). */
    static final String EXTRA_SURAH = "surah";

    private WebView web;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        web = Web.create(this, false, getIntent().getIntExtra(EXTRA_SURAH, -1));
        setContentView(web);
        AdhanScheduler.schedule(this);
        FridayScheduler.schedule(this, 5_000);
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK && web.canGoBack()) {
            web.goBack();
            return true;
        }
        return super.onKeyDown(code, e);
    }

    @Override
    protected void onResume() { super.onResume(); visible = true; }

    @Override
    protected void onPause() { visible = false; super.onPause(); }

    /** Leaving the app (Home, another app) closes it, so Quran or adhan audio doesn't keep playing unseen. */
    @Override
    protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations()) finish();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
