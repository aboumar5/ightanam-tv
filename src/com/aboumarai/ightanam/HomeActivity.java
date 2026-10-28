package com.aboumarai.ightanam;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.icu.text.SimpleDateFormat;
import android.icu.util.ULocale;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * A quiet home screen: the time, Hijri date and next prayer on top, and one row of app banners.
 * Shows every app that has a TV launcher entry, in {@link #ORDER} first, then the rest by name.
 */
public class HomeActivity extends Activity {
    /** Apps shown first, in this order (anything else installed follows). */
    private static final List<String> ORDER = Arrays.asList(
            "com.google.android.youtube.tv", "org.jellyfin.androidtv", "com.aboumarai.ightanam");
    /** Launcher entries that are reachable from the gear button instead of a tile. */
    private static final List<String> HIDDEN = Arrays.asList(
            "com.android.tv.settings", "com.android.vending");

    private static final int GOLD = 0xFFE9C069, INK = 0xFFF5F2E8, MUTED = 0xFF9AA3CC;
    private static final ULocale AR = new ULocale("ar_SA@calendar=gregorian");
    private static final ULocale HIJRI = new ULocale("ar_SA@calendar=islamic-umalqura");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView clock, hijri, greg, next;
    private LinearLayout row;
    private float dp;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateTop();
            // Next whole minute.
            handler.postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000 + 50);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;

        FrameLayout root = new FrameLayout(this);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackground(new NightSky());

        // Top: time and dates on the right, next prayer under them.
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setGravity(Gravity.START);
        clock = text(88, INK, true);
        clock.setTypeface(font("black", true));
        clock.setLetterSpacing(0.02f);
        hijri = text(24, GOLD, true);
        greg = text(16, MUTED, false);
        next = text(17, GOLD, true);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0x29E9C069);
        pill.setCornerRadius(999);
        next.setBackground(pill);
        next.setPadding(px(16), px(6), px(16), px(6));
        top.addView(clock);
        hijri.setPadding(0, px(8), 0, 0);
        greg.setPadding(0, px(8), 0, 0);
        top.addView(hijri);
        top.addView(greg);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        np.topMargin = px(14);
        top.addView(next, np);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        tp.setMargins(px(56), px(36), px(140), 0);
        root.addView(top, tp);

        // Gear (system settings) in the opposite corner.
        TextView gear = text(22, MUTED, false);
        gear.setText("⚙");
        gear.setGravity(Gravity.CENTER);
        gear.setFocusable(true);
        gear.setBackground(tileBg(false, 999));
        gear.setOnFocusChangeListener((v, f) -> { v.setBackground(tileBg(f, 999)); v.animate().scaleX(f ? 1.15f : 1).scaleY(f ? 1.15f : 1).setDuration(120); });
        gear.setOnClickListener(v -> launch(new Intent(android.provider.Settings.ACTION_SETTINGS)));
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(px(48), px(48), Gravity.TOP | Gravity.END);
        gp.setMargins(px(56), px(44), px(56), 0);
        root.addView(gear, gp);

        // Bottom: one row of app banners.
        row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(px(46), px(16), px(46), px(16));
        row.setClipToPadding(false);
        row.setClipChildren(false);
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setFillViewport(true);   // centers the row when it fits
        scroll.addView(row, new FrameLayout.LayoutParams(-2, -2));
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        rp.bottomMargin = px(40);
        root.addView(scroll, rp);

        setContentView(root);
        AdhanScheduler.schedule(this);
        FridayScheduler.schedule(this, 5_000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadApps();
        handler.removeCallbacks(tick);
        tick.run();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    /** Home stays home: Back does nothing here. */
    @Override
    public void onBackPressed() {}

    private void loadApps() {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> found = pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
        List<ApplicationInfo> apps = new ArrayList<>();
        for (ResolveInfo r : found) {
            ApplicationInfo a = r.activityInfo.applicationInfo;
            if (HIDDEN.contains(a.packageName)) continue;
            boolean dup = false;
            for (ApplicationInfo x : apps) if (x.packageName.equals(a.packageName)) dup = true;
            if (!dup) apps.add(a);
        }
        apps.sort((x, y) -> {
            int ix = ORDER.indexOf(x.packageName), iy = ORDER.indexOf(y.packageName);
            if (ix < 0) ix = 999;
            if (iy < 0) iy = 999;
            if (ix != iy) return ix - iy;
            return pm.getApplicationLabel(x).toString().compareTo(pm.getApplicationLabel(y).toString());
        });

        View focused = getCurrentFocus();
        Object keep = focused != null ? focused.getTag() : null;
        row.removeAllViews();
        View toFocus = null;
        for (ApplicationInfo a : apps) {
            View tile = tile(pm, a);
            row.addView(tile);
            if (toFocus == null || a.packageName.equals(keep)) toFocus = tile;
        }
        if (toFocus != null) toFocus.requestFocus();
    }

    private View tile(PackageManager pm, ApplicationInfo a) {
        int w = px(148), h = px(83), r = px(12);
        FrameLayout box = new FrameLayout(this);
        box.setTag(a.packageName);
        box.setFocusable(true);
        box.setPadding(px(3), px(3), px(3), px(3));
        box.setBackground(tileBg(false, r));
        box.setClipToOutline(true);

        Drawable banner = pm.getApplicationBanner(a);
        if (banner != null) {
            ImageView img = new ImageView(this);
            img.setImageDrawable(banner);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setClipToOutline(true);
            img.setBackground(round(0xFF1A2050, r - px(3)));
            box.addView(img, new FrameLayout.LayoutParams(-1, -1));
        } else {
            // No banner: icon over the app's name.
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            col.setBackground(round(0xFF1A2050, r - px(3)));
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(pm.getApplicationIcon(a));
            col.addView(icon, new LinearLayout.LayoutParams(px(44), px(44)));
            TextView name = text(13, INK, true);
            name.setText(pm.getApplicationLabel(a));
            col.addView(name);
            box.addView(col, new FrameLayout.LayoutParams(-1, -1));
        }

        box.setOnFocusChangeListener((v, f) -> {
            v.setBackground(tileBg(f, r));
            v.animate().scaleX(f ? 1.1f : 1).scaleY(f ? 1.1f : 1).translationZ(f ? px(8) : 0).setDuration(140);
        });
        box.setOnClickListener(v -> {
            Intent i = pm.getLeanbackLaunchIntentForPackage(a.packageName);
            if (i == null) i = pm.getLaunchIntentForPackage(a.packageName);
            launch(i);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.setMarginStart(px(10));
        lp.setMarginEnd(px(10));
        box.setLayoutParams(lp);
        return box;
    }

    private void launch(Intent i) {
        if (i == null) return;
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) {}
    }

    private void updateTop() {
        Date now = new Date();
        clock.setText(new SimpleDateFormat("h:mm", AR).format(now));
        hijri.setText(new SimpleDateFormat("EEEE، d MMMM y", HIJRI).format(now) + " هـ");
        greg.setText(new SimpleDateFormat("d MMMM y", AR).format(now) + " م");

        long[] at = new long[1];
        String key = nextPrayer(at);
        if (key == null) { next.setVisibility(View.GONE); return; }
        long mins = Math.max(0, (at[0] - now.getTime() + 59_999) / 60_000);
        String left = mins >= 60 ? (mins / 60) + " س " + (mins % 60) + " د" : mins + " د";
        next.setText(AdhanService.nameOf(key) + "  ·  بعد " + toAr(left));
        next.setVisibility(View.VISIBLE);
    }

    /** Next prayer from the times the page synced, or computed here as a fallback. */
    private String nextPrayer(long[] at) {
        long now = System.currentTimeMillis();
        String key = null;
        for (String item : AdhanScheduler.prefs(this).getString("times", "").split(",")) {
            int i = item.indexOf(':');
            if (i < 0) continue;
            long t = Long.parseLong(item.substring(i + 1));
            if (t > now && (key == null || t < at[0])) { at[0] = t; key = item.substring(0, i); }
        }
        if (key != null) return key;
        float lat = AdhanScheduler.prefs(this).getFloat("lat", 24.7136f);
        float lng = AdhanScheduler.prefs(this).getFloat("lng", 46.6753f);
        Calendar day = Calendar.getInstance();
        for (int d = 0; d < 2; d++) {
            long[] t = PrayerTimes.forDay(lat, lng,
                    day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH));
            for (int i = 0; i < t.length; i++) if (t[i] > now) { at[0] = t[i]; return PrayerTimes.KEYS[i]; }
            day.add(Calendar.DAY_OF_MONTH, 1);
        }
        return null;
    }

    private static String toAr(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) b.append(c >= '0' && c <= '9' ? (char) ('٠' + (c - '0')) : c);
        return b.toString();
    }

    private GradientDrawable tileBg(boolean focused, int radius) {
        GradientDrawable d = round(focused ? 0x33E9C069 : 0x0DFFFFFF, radius);
        d.setStroke(px(focused ? 3 : 1), focused ? GOLD : 0x1AFFFFFF);
        return d;
    }

    private static GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private TextView text(int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTextDirection(View.TEXT_DIRECTION_RTL);
        t.setTypeface(font(bold ? "bold" : "regular", bold));
        t.setIncludeFontPadding(false);
        return t;
    }

    /** Thmanyah if the build bundled it (assets/font-*.otf, see build.ps1), else the system sans-serif. */
    private Typeface font(String weight, boolean bold) {
        for (String ext : new String[]{".ttf", ".otf"}) {
            try { return Typeface.createFromAsset(getAssets(), "font-" + weight + ext); } catch (RuntimeException ignored) {}
        }
        return Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL);
    }

    private int px(float v) { return (int) (v * dp + 0.5f); }

    /**
     * Deep night background: dark navy gradient, a soft gold glow behind the clock, a blue glow
     * low on the other side, and a barely-visible lattice of eight-pointed stars that fades out
     * toward the app row.
     */
    private static final class NightSky extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path star = new Path();

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            float w = b.width(), h = b.height();
            paint.setStyle(Paint.Style.FILL);

            paint.setShader(new LinearGradient(0, 0, 0, h,
                    new int[]{0xFF0F1433, 0xFF090C22, 0xFF04050F}, new float[]{0, .55f, 1}, Shader.TileMode.CLAMP));
            c.drawRect(b, paint);
            glow(c, w * 0.86f, h * 0.2f, w * 0.48f, 0x2EE9C069);
            glow(c, w * 0.08f, h * 1.05f, w * 0.55f, 0x402A3A9C);

            // Lattice, strongest at the top.
            float step = h / 6.5f, r = step * 0.3f;
            paint.setShader(new LinearGradient(0, 0, 0, h * 0.75f,
                    0x14E9C069, 0x00E9C069, Shader.TileMode.CLAMP));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, h / 900f));
            for (float y = 0; y < h * 0.8f; y += step) {
                for (float x = (y / step) % 2 == 0 ? 0 : step / 2; x < w + step; x += step) {
                    star.reset();
                    for (int rot = 0; rot < 2; rot++) {
                        for (int k = 0; k < 4; k++) {
                            double t = Math.toRadians(rot * 45 + 45 + 90 * k);
                            float px = x + r * (float) Math.cos(t), py = y + r * (float) Math.sin(t);
                            if (k == 0) star.moveTo(px, py); else star.lineTo(px, py);
                        }
                        star.close();
                    }
                    c.drawPath(star, paint);
                }
            }

            // Darken the bottom so the app banners stand out.
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0, h * 0.6f, 0, h, 0x00000000, 0x66000000, Shader.TileMode.CLAMP));
            c.drawRect(0, h * 0.6f, w, h, paint);
            paint.setShader(null);
        }

        private void glow(Canvas c, float x, float y, float rad, int color) {
            paint.setShader(new RadialGradient(x, y, rad, color, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, rad, paint);
        }

        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(ColorFilter f) {}
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }
}
