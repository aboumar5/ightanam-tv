package com.aboumarai.ightanam;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Shows a small adhan card in the corner over whatever is playing, pauses it, plays the adhan,
 * then removes the card and resumes playback. Back (or OK) on the remote stops it early.
 * Also shows the Friday reminder cards (kind = kahf / tabkeer / dua), which don't pause anything.
 */
public class AdhanService extends Service {
    private static final String BASE = "https://aboumarai.com";
    private static final long TIMEOUT_MS = 6 * 60_000;
    /** How long a Friday reminder card stays up if nobody presses anything. */
    private static final long REMINDER_MS = 60_000;
    private static final int GOLD = 0xFFD4AF37;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private AudioManager am;
    private AudioFocusRequest focus;
    private MediaPlayer player;
    private View card;
    private boolean resumeAfter;

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, notification());
        if (card != null) return START_NOT_STICKY;   // already showing
        String kind = intent != null ? intent.getStringExtra("kind") : null;
        if (kind == null) kind = "adhan";
        String key = intent != null ? intent.getStringExtra("key") : null;
        if (key == null) key = "asr";

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        am = (AudioManager) getSystemService(AUDIO_SERVICE);

        if (!"adhan".equals(kind)) {
            // Friday reminder: a quiet card with a soft chime; the video keeps playing.
            showFridayCard(kind);
            chime();
            handler.postDelayed(this::finish, REMINDER_MS);
            return START_NOT_STICKY;
        }

        resumeAfter = am.isMusicActive();
        if (resumeAfter) sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE);

        showCard(key);
        play(key);
        handler.postDelayed(this::finish, TIMEOUT_MS);
        return START_NOT_STICKY;
    }

    private void showFridayCard(String kind) {
        String title, body, hint;
        boolean kahf = "kahf".equals(kind);
        switch (kind) {
            case "kahf":
                title = "📖  جمعة مباركة";
                body = "لا تنسَ قراءة سورة الكهف";
                hint = "زر الوسط: تشغيل سورة الكهف   ·   رجوع: إخفاء";
                break;
            case "tabkeer":
                title = "🕌  التبكير إلى الجمعة";
                body = "باقي ساعة وربع على صلاة الجمعة";
                hint = "↩ رجوع: إخفاء";
                break;
            default:
                title = "🤲  ساعة الإجابة";
                body = "آخر ساعة من يوم الجمعة، أكثِر من الدعاء";
                hint = "↩ رجوع: إخفاء";
                break;
        }
        showCard(title, body, hint, e -> {
            if (kahf && (e == KeyEvent.KEYCODE_DPAD_CENTER || e == KeyEvent.KEYCODE_ENTER)) {
                startActivity(new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra(MainActivity.EXTRA_SURAH, 17));   // index of Al-Kahf in the page's list
            }
            finish();
        });
    }

    private void chime() {
        try {
            android.media.Ringtone r = android.media.RingtoneManager.getRingtone(this,
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION));
            if (r != null) r.play();
        } catch (Exception ignored) {}
    }

    static String nameOf(String key) {
        switch (key) {
            case "fajr": return "الفجر";
            case "dhuhr": return "الظهر";
            case "maghrib": return "المغرب";
            case "isha": return "العشاء";
            default: return "العصر";
        }
    }

    /** Same files (and versions) as the site's muezzin list; "?v=2" are the trimmed recordings. */
    private String audioUrl(String key) {
        if ("fajr".equals(key)) return BASE + "/adhan/adhan-fajr.mp3";
        switch (AdhanScheduler.prefs(this).getString("muezzin", "alafasy")) {
            case "arkani": return BASE + "/adhan/adhan-arkani.mp3?v=2";
            case "qatami": return BASE + "/adhan/adhan-qatami.mp3";
            case "zahrani": return BASE + "/adhan/adhan-zahrani.mp3";
            default: return BASE + "/adhan/adhan.mp3?v=2";
        }
    }

    private void showCard(String key) {
        showCard("🕌  أذان " + nameOf(key), "حان الآن وقت صلاة " + nameOf(key),
                "↩ رجوع: إيقاف الأذان ومتابعة المشاهدة", k -> finish());
    }

    /** Called with the key code when Back / OK is pressed on the card. */
    private interface OnKey { void onKey(int keyCode); }

    private void showCard(String title, String body, String hintText, final OnKey onKey) {
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(this) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                int k = e.getKeyCode();
                if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) {
                    if (e.getAction() == KeyEvent.ACTION_UP) onKey.onKey(k);
                    return true;
                }
                return super.dispatchKeyEvent(e);
            }
        };
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.END);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int pad = (int) (16 * dp);
        box.setPadding(pad + pad / 2, pad, pad + pad / 2, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF0101828);
        bg.setCornerRadius(18 * dp);
        bg.setStroke((int) (1.5f * dp), GOLD);
        box.setBackground(bg);
        box.setFocusable(true);

        box.addView(text(title, 26, GOLD, true));
        box.addView(text(body, 16, Color.WHITE, false));
        TextView hint = text(hintText, 12, 0xFFA8B0C8, false);
        hint.setPadding(0, (int) (8 * dp), 0, 0);
        box.addView(hint);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // Focusable so the remote's Back reaches the card instead of the app behind it.
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.RIGHT;
        lp.x = (int) (28 * dp);
        lp.y = (int) (28 * dp);
        lp.windowAnimations = android.R.style.Animation_Toast;
        wm.addView(box, lp);
        box.requestFocus();
        card = box;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.END);
        t.setTextDirection(View.TEXT_DIRECTION_RTL);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private void play(String key) {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs).build();
        am.requestAudioFocus(focus);
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(attrs);
            player.setDataSource(audioUrl(key));
            player.setOnPreparedListener(MediaPlayer::start);
            player.setOnCompletionListener(mp -> finish());
            player.setOnErrorListener((mp, what, extra) -> {
                Log.w(AdhanScheduler.TAG, "adhan audio error " + what);
                handler.postDelayed(this::finish, 20_000);   // keep the card up briefly anyway
                return true;
            });
            player.prepareAsync();
        } catch (Exception e) {
            Log.w(AdhanScheduler.TAG, "adhan audio failed", e);
            handler.postDelayed(this::finish, 20_000);
        }
    }

    /** Adhan finished, timed out, or the viewer pressed Back. */
    private void finish() {
        handler.removeCallbacksAndMessages(null);
        if (player != null) { try { player.stop(); } catch (Exception ignored) {} player.release(); player = null; }
        if (focus != null) { am.abandonAudioFocusRequest(focus); focus = null; }
        if (card != null) { wm.removeViewImmediate(card); card = null; }
        if (resumeAfter) {
            resumeAfter = false;
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY);
        }
        stopForeground(true);
        stopSelf();
    }

    private void sendMediaKey(int code) {
        long t = SystemClock.uptimeMillis();
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0));
    }

    private Notification notification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("adhan", "الأذان", NotificationManager.IMPORTANCE_LOW));
        return new Notification.Builder(this, "adhan")
                .setContentTitle("الأذان")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .build();
    }

    @Override
    public void onDestroy() {
        if (card != null || player != null) finish();
        super.onDestroy();
    }
}
