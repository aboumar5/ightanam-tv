package com.aboumarai.ightanam;

import android.content.Context;
import android.graphics.Color;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Shared WebView setup for the app and the screensaver. */
final class Web {
    static final String URL = "https://aboumarai.com/ightanam";

    /**
     * Sends the page's saved city, chosen muezzin and the exact prayer times for the next week
     * (computed with the page's own adhan.js) to the app, so the adhan card matches the clock.
     */
    private static final String SYNC_JS =
            "function igSync(){try{"
            + "var l=JSON.parse(localStorage.getItem('adhan_clock_loc_v1')||'{\"lat\":24.7136,\"lng\":46.6753}');"
            + "var A=window.adhan, out=[];"
            + "if(A){var p=A.CalculationMethod.UmmAlQura(); p.madhab=A.Madhab.Shafi; var c=new A.Coordinates(l.lat,l.lng);"
            + "for(var i=0;i<7;i++){var d=new Date(); d.setDate(d.getDate()+i); var pt=new A.PrayerTimes(c,d,p);"
            + "['fajr','dhuhr','asr','maghrib','isha'].forEach(function(k){out.push(k+':'+pt[k].getTime());});}}"
            + "IghtanamTV.sync(l.lat,l.lng,localStorage.getItem('adhan_muezzin')||'alafasy',out.join(','));"
            + "}catch(e){}}"
            + "igSync(); setInterval(igSync,60000);";

    /**
     * Remote-friendly layout for the app (not the screensaver): everything fits on one screen,
     * buttons the TV can't use are hidden, the rest are bigger with a clear gold focus ring,
     * and the arrows always land on a button.
     */
    private static final String TV_CSS =
            "body.tv .clock{font-size:min(14vw,16vh)!important}"
            + "body.tv .hijri{font-size:min(4vw,5.2vh)} body.tv .greg{font-size:min(2.4vw,3.2vh);margin-top:0}"
            + "body.tv .next{margin-top:.6vh;gap:2px} body.tv .next .nm{font-size:min(5vw,6.5vh)}"
            + "body.tv .next .cd,body.tv .next .since{margin-top:2px;font-size:min(2.2vw,3vh)}"
            + "body.tv .ribbon{margin-top:.6vh} body.tv .ribbon .txt{font-size:min(2.6vw,3.6vh)}"
            + "body.tv .grid{margin-top:1vh}"
            + "body.tv #fsBtn,body.tv #bigBtn,body.tv .exitbig{display:none!important}"
            + "body.tv .bar{gap:12px;margin-top:1.6vh;flex-wrap:nowrap}"
            + "body.tv .btn{font-size:min(1.6vw,3vh);padding:1vh 1.6vw;border-width:2px;white-space:nowrap}"
            + "body.tv .pillbtn{font-size:min(1.7vw,3vh);padding:.9vh 1.6vw}"
            + "body.tv button{transition:transform .12s,box-shadow .12s}"
            + "body.tv button:focus{outline:3px solid var(--gold);outline-offset:3px;transform:scale(1.1);"
            + "box-shadow:0 0 28px var(--gold-soft);background:var(--gold-soft);position:relative;z-index:2}"
            + "body.tv .su:focus{outline:3px solid var(--gold);transform:scale(1.04)}"
            + "body.tv .su .nm{font-size:min(1.8vw,3.2vh)}"
            // The page can't scroll on a TV, so keep the Quran player pinned on screen.
            + "#player.show{position:fixed;left:50%;bottom:24px;transform:translateX(-50%);z-index:60;"
            + "background:rgba(16,24,40,.94);border:1px solid #d4af37;border-radius:18px;padding:10px 18px}";

    private static final String TV_JS =
            "document.body.classList.remove('big');document.body.classList.add('tv');"
            + "var ts=document.createElement('style');ts.textContent=\"" + TV_CSS + "\";document.head.appendChild(ts);"
            + "function igVisible(e){return e&&e.offsetParent!==null&&!e.disabled;}"
            + "function igFirst(){var m=document.querySelector('.modal.show');"
            + "var s=m?m.querySelectorAll('button'):document.querySelectorAll('#player.show button,.bar button');"
            + "for(var i=0;i<s.length;i++)if(igVisible(s[i]))return s[i];return null;}"
            // Arrow keys with nothing (or a hidden button) focused jump to the first useful button.
            + "document.addEventListener('keydown',function(e){if(e.key.indexOf('Arrow')!==0)return;"
            + "var a=document.activeElement;if(a&&a!==document.body&&igVisible(a)&&(!document.querySelector('.modal.show')||a.closest('.modal')))return;"
            + "var f=igFirst();if(f){f.focus();e.preventDefault();}},true);"
            + "document.addEventListener('focusin',function(e){if(e.target.scrollIntoView)e.target.scrollIntoView({block:'nearest'});});"
            + "setTimeout(function(){var q=document.getElementById('quranBtn');if(q&&document.activeElement===document.body)q.focus();},400);";

    // Presses «ابدأ» (enables adhan audio), syncs settings, and applies the big screensaver layout.
    private static String startJs(boolean big, int surah) {
        StringBuilder js = new StringBuilder("(function(){")
                .append("var b=document.getElementById('startBtn'); if(b) b.click();")
                .append(SYNC_JS);
        if (surah >= 0) {
            // Start that surah in the page's own Quran player.
            js.append("setTimeout(function(){var s=document.querySelector('#surahs [data-i=\"")
              .append(surah).append("\"]'); if(s) s.click();")
              // Focus the pause button so OK on the remote pauses / resumes.
              .append("var q=document.getElementById('qPause'); if(q) q.focus();")
              .append("},800);");
        }
        if (!big) js.append(TV_JS);
        if (big) {
            js.append("document.body.classList.add('big');")
              // Nothing to click here: hide the exit button and shrink the clock so the prayer row fits.
              .append("var st=document.createElement('style');")
              .append("st.textContent='.exitbig{display:none!important}")
              .append("body.big .clock{font-size:min(22vw,24vh)!important}")
              .append("body.big .app{min-height:100vh;box-sizing:border-box}';")
              .append("document.head.appendChild(st);");
        }
        return js.append("})();").toString();
    }

    static WebView create(Context ctx, boolean big) {
        return create(ctx, big, -1);
    }

    /** @param surah index into the page's surah list to start playing, or -1. */
    static WebView create(Context ctx, final boolean big, final int surah) {
        final Context app = ctx.getApplicationContext();
        WebView w = new WebView(ctx);
        w.setBackgroundColor(Color.BLACK);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        w.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void sync(double lat, double lng, String muezzin, String times) {
                AdhanScheduler.sync(app, lat, lng, muezzin, times);
            }
        }, "IghtanamTV");
        w.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage m) {
                android.util.Log.i(AdhanScheduler.TAG, "page: " + m.message() + " @" + m.lineNumber());
                return true;
            }
        });
        w.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // Small delay so the page's own scripts have attached their handlers.
                view.postDelayed(() -> view.evaluateJavascript(startJs(big, surah), null), 1500);
            }
        });
        // Always fetch the latest page so updates to the site show up on the TV right away.
        w.clearCache(true);
        w.loadUrl(URL);
        return w;
    }

    private Web() {}
}
