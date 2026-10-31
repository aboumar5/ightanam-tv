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
 * The row holds the viewer's picks (long-press OK in "all apps" adds or removes one); until they
 * pick, it shows every TV app, {@link #ORDER} first. The grid button lists everything installed.
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

        root = new FrameLayout(this);
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

        // Opposite corner: system settings, and next to it all apps.
        root.addView(roundButton(Glyph.GEAR, () -> launch(new Intent(android.provider.Settings.ACTION_SETTINGS))), corner(0));
        appsBtn = roundButton(Glyph.APPS, this::showAllApps);
        root.addView(appsBtn, corner(1));
        inputsBtn = roundButton(Glyph.INPUT, this::showInputs);
        root.addView(inputsBtn, corner(2));

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

    /** Home stays home: Back only closes the all-apps grid. */
    @Override
    public void onBackPressed() { hideAllApps(); }

    /** Home pressed while already home: back to the main view. */
    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        hideAllApps();
    }

    /**
     * TV apps (with {@code all}, phone-style launcher apps too, plus Settings and Play Store),
     * one entry per package, in {@link #ORDER} then by name.
     */
    private List<ApplicationInfo> queryApps(boolean all) {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> found = new ArrayList<>(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        int tvCount = found.size();
        if (all) found.addAll(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        List<ApplicationInfo> apps = new ArrayList<>();
        for (int i = 0; i < found.size(); i++) {
            ApplicationInfo a = found.get(i).activityInfo.applicationInfo;
            if (!all && HIDDEN.contains(a.packageName)) continue;
            // Phone-style entries from the system image are factory demos and setup tools.
            if (i >= tvCount && (a.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            boolean dup = false;
            for (ApplicationInfo x : apps) if (x.packageName.equals(a.packageName)) dup = true;
            if (!dup) apps.add(a);
        }
        apps.sort((x, y) -> {
            int ix = all ? 0 : ORDER.indexOf(x.packageName), iy = all ? 0 : ORDER.indexOf(y.packageName);
            if (ix < 0) ix = 999;
            if (iy < 0) iy = 999;
            if (ix != iy) return ix - iy;
            return pm.getApplicationLabel(x).toString().compareTo(pm.getApplicationLabel(y).toString());
        });
        return apps;
    }

    /** The apps on the home row, in order: the viewer's picks, or every TV app until they pick. */
    private List<String> favorites() {
        String saved = AdhanScheduler.prefs(this).getString("home_apps", null);
        List<String> out = new ArrayList<>();
        if (saved != null) {
            for (String p : saved.split(",")) if (!p.isEmpty()) out.add(p);
        } else {
            for (ApplicationInfo a : queryApps(false)) out.add(a.packageName);
        }
        return out;
    }

    /** Adds the app to the end of the row, or removes it. Returns true if it's now on the row. */
    private boolean toggleFavorite(String pkg) {
        List<String> fav = favorites();
        boolean add = !fav.remove(pkg);
        if (add) fav.add(pkg);
        AdhanScheduler.prefs(this).edit().putString("home_apps", String.join(",", fav)).apply();
        android.widget.Toast.makeText(this, add ? "أُضيف للشريط" : "انشال من الشريط", android.widget.Toast.LENGTH_SHORT).show();
        return add;
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        View focused = getCurrentFocus();
        Object keep = focused != null ? focused.getTag() : null;
        row.removeAllViews();
        View toFocus = null;
        List<ApplicationInfo> installed = queryApps(true);
        for (String pkg : favorites()) {
            for (ApplicationInfo a : installed) {
                if (!a.packageName.equals(pkg)) continue;
                View tile = tile(pm, a);
                // Long-press OK on a row tile takes it off the row.
                tile.setOnLongClickListener(v -> { toggleFavorite(pkg); loadApps(); return true; });
                row.addView(tile, rowParams());
                if (toFocus == null || pkg.equals(keep)) toFocus = tile;
            }
        }
        if (toFocus != null && allApps == null && !appsBtn.isFocused()) toFocus.requestFocus();
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(148), px(83));
        lp.setMarginStart(px(10));
        lp.setMarginEnd(px(10));
        return lp;
    }

    private FrameLayout root;
    /** The full-screen layer (all apps or inputs) on top, and the button that opened it. */
    private View allApps, opener;
    private View appsBtn, inputsBtn;

    /**
     * The TV's physical inputs (HDMI and the like, not the tuner), as tiles that switch to them.
     * A device the TV recognizes over HDMI-CEC (e.g. a game console) shows under its port.
     */
    private void showInputs() {
        if (allApps != null) return;
        android.media.tv.TvInputManager tim = (android.media.tv.TvInputManager) getSystemService(TV_INPUT_SERVICE);
        List<android.media.tv.TvInputInfo> all = tim != null ? tim.getTvInputList() : new ArrayList<>();
        FrameLayout layer = layer("مصادر الإدخال", "اختر الجهاز اللي تبي تحوّل له. المنافذ الموصولة بالذهبي.");

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.HORIZONTAL);
        list.setGravity(Gravity.CENTER);
        list.setClipChildren(false);   // room for the focused tile to grow
        list.setClipToPadding(false);
        list.setPadding(0, px(16), 0, px(16));
        layer.setClipChildren(false);
        View first = null, firstConnected = null;
        for (android.media.tv.TvInputInfo in : all) {
            if (in.getParentId() != null || in.getType() == android.media.tv.TvInputInfo.TYPE_TUNER
                    || in.isPassthroughInput() == false) continue;
            // Only an active signal counts: empty HDMI ports report "standby" on some TVs.
            boolean connected = tim.getInputState(in.getId()) == android.media.tv.TvInputManager.INPUT_STATE_CONNECTED;
            String device = null;
            for (android.media.tv.TvInputInfo child : all) {
                if (in.getId().equals(child.getParentId())) device = String.valueOf(child.loadLabel(this));
            }
            CharSequence custom = in.loadCustomLabel(this);
            String name = String.valueOf(custom != null && custom.length() > 0 ? custom : in.loadLabel(this));
            View t = inputTile(name, device != null ? device : (connected ? "موصول" : "غير موصول"), connected, in.getId());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(168), px(100));
            lp.setMargins(px(10), 0, px(10), 0);
            list.addView(t, lp);
            if (first == null) first = t;
            if (connected && device != null && firstConnected == null) firstConnected = t;
        }
        if (first == null) {
            TextView none = text(16, MUTED, false);
            none.setText("ما لقيت مداخل في هذي الشاشة.");
            list.addView(none);
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        layer.addView(list, lp);
        show(layer, inputsBtn, firstConnected != null ? firstConnected : first);
    }

    private View inputTile(String name, String sub, boolean connected, String inputId) {
        int r = px(12);
        FrameLayout box = frame(inputId, r);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setBackground(round(connected ? 0xFF1C2350 : 0xFF11152F, r - px(3)));
        TextView n = text(20, connected ? GOLD : MUTED, true);
        n.setText(name);
        n.setGravity(Gravity.CENTER);
        TextView s = text(13, connected ? INK : MUTED, false);
        s.setText(sub);
        s.setGravity(Gravity.CENTER);
        s.setPadding(0, px(6), 0, 0);
        col.addView(n);
        col.addView(s);
        box.addView(col, new FrameLayout.LayoutParams(-1, -1));
        box.setOnClickListener(v -> {
            hideAllApps();
            launch(new Intent(Intent.ACTION_VIEW, android.media.tv.TvContract.buildChannelUriForPassthroughInput(inputId)));
        });
        return box;
    }

    /** Full-screen layer with a title and a hint line. */
    private FrameLayout layer(String titleText, String hintText) {
        FrameLayout layer = new FrameLayout(this);
        layer.setBackground(new NightSky());
        layer.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(26, INK, true);
        title.setText(titleText);
        TextView hint = text(13, MUTED, false);
        hint.setText(hintText);
        hint.setPadding(0, px(8), 0, 0);
        head.addView(title);
        head.addView(hint);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        tp.setMargins(px(56), px(30), px(56), 0);
        layer.addView(head, tp);
        return layer;
    }

    private void show(FrameLayout layer, View from, View focus) {
        root.addView(layer, new FrameLayout.LayoutParams(-1, -1));
        allApps = layer;
        opener = from;
        if (focus != null) focus.requestFocus();
    }

    /** Round icon button for the top corner. */
    private View roundButton(int kind, Runnable onClick) {
        ImageView b = new ImageView(this);
        b.setImageDrawable(new Glyph(kind));
        b.setColorFilter(MUTED);
        b.setPadding(px(13), px(13), px(13), px(13));
        b.setFocusable(true);
        b.setBackground(tileBg(false, 999));
        b.setOnFocusChangeListener((v, f) -> {
            v.setBackground(tileBg(f, 999));
            ((ImageView) v).setColorFilter(f ? GOLD : MUTED);
            v.animate().scaleX(f ? 1.15f : 1).scaleY(f ? 1.15f : 1).setDuration(120);
        });
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    /** Icons drawn to match each other: a gear, a 3x3 grid of rounded squares (all apps), or an input. */
    private static final class Glyph extends Drawable {
        static final int GEAR = 0, APPS = 1, INPUT = 2;
        private final int kind;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Glyph(int kind) { this.kind = kind; p.setColor(0xFFFFFFFF); }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            float s = Math.min(b.width(), b.height());
            float x0 = b.exactCenterX() - s / 2, y0 = b.exactCenterY() - s / 2;
            if (kind == INPUT) {
                // A screen outline with an arrow coming in from the side.
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s * 0.11f);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeJoin(Paint.Join.ROUND);
                c.drawRoundRect(x0 + s * 0.22f, y0 + s * 0.14f, x0 + s * 0.94f, y0 + s * 0.72f, s * 0.1f, s * 0.1f, p);
                c.drawLine(x0 + s * 0.46f, y0 + s * 0.88f, x0 + s * 0.7f, y0 + s * 0.88f, p);
                float ay = y0 + s * 0.43f;
                c.drawLine(x0 + s * 0.06f, ay, x0 + s * 0.6f, ay, p);
                c.drawLine(x0 + s * 0.6f, ay, x0 + s * 0.46f, ay - s * 0.14f, p);
                c.drawLine(x0 + s * 0.6f, ay, x0 + s * 0.46f, ay + s * 0.14f, p);
            } else if (kind == GEAR) {
                float cx = x0 + s / 2, cy = y0 + s / 2;
                p.setStyle(Paint.Style.FILL);
                // Eight teeth, then the ring over them, then the hole.
                for (int k = 0; k < 8; k++) {
                    c.save();
                    c.rotate(k * 45, cx, cy);
                    c.drawRoundRect(cx - s * 0.1f, y0 + s * 0.02f, cx + s * 0.1f, cy - s * 0.3f, s * 0.04f, s * 0.04f, p);
                    c.restore();
                }
                Path ring = new Path();
                ring.setFillType(Path.FillType.EVEN_ODD);
                ring.addCircle(cx, cy, s * 0.36f, Path.Direction.CW);
                ring.addCircle(cx, cy, s * 0.15f, Path.Direction.CW);
                c.drawPath(ring, p);
            } else {
                float cell = s / 3, sq = cell * 0.62f, r = cell * 0.16f;
                p.setStyle(Paint.Style.FILL);
                for (int i = 0; i < 3; i++) {
                    for (int j = 0; j < 3; j++) {
                        float cx = x0 + cell * (i + 0.5f), cy = y0 + cell * (j + 0.5f);
                        c.drawRoundRect(cx - sq / 2, cy - sq / 2, cx + sq / 2, cy + sq / 2, r, r, p);
                    }
                }
            }
        }

        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** Position of the i-th round button, from the screen edge inward. */
    private FrameLayout.LayoutParams corner(int i) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(48), px(48), Gravity.TOP | Gravity.END);
        lp.setMargins(0, px(44), px(56 + i * 62), 0);
        lp.setMarginEnd(px(56 + i * 62));
        return lp;
    }

    /** Full-screen grid of every app on the TV, over the home screen. */
    private void showAllApps() {
        if (allApps != null) return;
        PackageManager pm = getPackageManager();
        FrameLayout layer = layer("كل التطبيقات",
                "ضغطة مطوّلة على زر الوسط: إضافة التطبيق للشريط تحت أو إزالته  ·  ★ = في الشريط");

        android.widget.GridLayout grid = new android.widget.GridLayout(this);
        grid.setColumnCount(5);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding(0, px(12), 0, px(24));
        View first = null;
        List<String> fav = favorites();
        for (ApplicationInfo a : queryApps(true)) {
            FrameLayout t = tile(pm, a);
            // Star on apps that are on the home row; long-press OK toggles it.
            TextView star = text(13, 0xFF1A1400, true);
            star.setTypeface(Typeface.DEFAULT_BOLD);
            star.setText("★");
            star.setGravity(Gravity.CENTER);
            star.setBackground(round(GOLD, 999));
            star.setVisibility(fav.contains(a.packageName) ? View.VISIBLE : View.GONE);
            FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(px(22), px(22), Gravity.TOP | Gravity.END);
            sl.setMargins(px(5), px(5), px(5), px(5));
            t.addView(star, sl);
            t.setOnLongClickListener(v -> {
                star.setVisibility(toggleFavorite(a.packageName) ? View.VISIBLE : View.GONE);
                return true;
            });
            android.widget.GridLayout.LayoutParams lp = new android.widget.GridLayout.LayoutParams();
            lp.width = px(148);
            lp.height = px(83);
            lp.setMargins(px(10), px(10), px(10), px(10));
            grid.addView(t, lp);
            if (first == null) first = t;
        }
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setClipChildren(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(grid, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-1, -1);
        sp.setMargins(0, px(90), 0, 0);
        layer.addView(scroll, sp);
        show(layer, appsBtn, first);
    }

    private void hideAllApps() {
        if (allApps == null) return;
        root.removeView(allApps);
        allApps = null;
        loadApps();   // picks may have changed
        if (opener != null) opener.requestFocus();
    }

    /** Focusable rounded frame that grows and turns gold when focused. */
    private FrameLayout frame(Object tag, int r) {
        FrameLayout box = new FrameLayout(this);
        box.setTag(tag);
        box.setFocusable(true);
        box.setPadding(px(3), px(3), px(3), px(3));
        box.setBackground(tileBg(false, r));
        box.setClipToOutline(true);
        box.setOnFocusChangeListener((v, f) -> {
            v.setBackground(tileBg(f, r));
            v.animate().scaleX(f ? 1.1f : 1).scaleY(f ? 1.1f : 1).translationZ(f ? px(8) : 0).setDuration(140);
        });
        return box;
    }

    private FrameLayout tile(PackageManager pm, ApplicationInfo a) {
        int r = px(12);
        FrameLayout box = frame(a.packageName, r);

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

        box.setOnClickListener(v -> {
            Intent i = pm.getLeanbackLaunchIntentForPackage(a.packageName);
            if (i == null) i = pm.getLaunchIntentForPackage(a.packageName);
            launch(i);
        });
        return box;
    }

    private void launch(Intent i) {
        if (i == null) return;
        // Same flags as the stock launchers: reuse the app's task but reset it to its start screen if stale.
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)); } catch (Exception ignored) {}
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
