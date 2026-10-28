package com.aboumarai.ightanam;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Umm al-Qura times (Fajr 18.5°, Isha = Maghrib + 90 min, Shafi'i Asr), matching the page's adhan.js setup.
 * Standard solar-position algorithm; accurate to about a minute, which is why alarms fire early
 * and the page itself plays the adhan at its exact time.
 */
final class PrayerTimes {
    static final String[] KEYS = {"fajr", "dhuhr", "asr", "maghrib", "isha"};

    /** Epoch millis for fajr, dhuhr, asr, maghrib, isha on the given calendar day (UTC date fields). */
    static long[] forDay(double lat, double lng, int year, int month, int day) {
        double jd = julian(year, month, day) - lng / (15.0 * 24.0);

        double fajr = sunAngleTime(jd, lat, 18.5, 5.0 / 24, true);
        double dhuhr = midDay(jd, 12.0 / 24);
        double asr = asrTime(jd, lat, 1, 13.0 / 24);
        double maghrib = sunAngleTime(jd, lat, 0.833, 18.0 / 24, false);
        double isha = maghrib + 1.5;

        double[] h = {fajr, dhuhr, asr, maghrib, isha};
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(year, month - 1, day, 0, 0, 0);
        long midnight = utc.getTimeInMillis();
        long[] out = new long[h.length];
        for (int i = 0; i < h.length; i++) {
            double utcHours = h[i] - lng / 15.0;
            // Round to the nearest minute like adhan.js does.
            out[i] = midnight + Math.round(utcHours * 60.0) * 60_000L;
        }
        return out;
    }

    private static double julian(int y, int m, int d) {
        if (m <= 2) { y -= 1; m += 12; }
        double a = Math.floor(y / 100.0);
        double b = 2 - a + Math.floor(a / 4.0);
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + b - 1524.5;
    }

    /** [declination (deg), equation of time (hours)] */
    private static double[] sun(double jd) {
        double d = jd - 2451545.0;
        double g = fix(357.529 + 0.98560028 * d, 360);
        double q = fix(280.459 + 0.98564736 * d, 360);
        double l = fix(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g), 360);
        double e = 23.439 - 0.00000036 * d;
        double ra = darctan2(dcos(e) * dsin(l), dcos(l)) / 15.0;
        double eqt = q / 15.0 - fix(ra, 24);
        double decl = darcsin(dsin(e) * dsin(l));
        return new double[]{decl, eqt};
    }

    private static double midDay(double jd, double t) {
        double eqt = sun(jd + t)[1];
        return fix(12 - eqt, 24);
    }

    private static double sunAngleTime(double jd, double lat, double angle, double t, boolean ccw) {
        double decl = sun(jd + t)[0];
        double noon = midDay(jd, t);
        double x = (-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat));
        double tt = darccos(Math.max(-1, Math.min(1, x))) / 15.0;
        return noon + (ccw ? -tt : tt);
    }

    private static double asrTime(double jd, double lat, double factor, double t) {
        double decl = sun(jd + t)[0];
        double angle = -Math.toDegrees(Math.atan(1.0 / (factor + Math.tan(Math.toRadians(Math.abs(lat - decl))))));
        return sunAngleTime(jd, lat, angle, t, false);
    }

    private static double fix(double a, double b) { a = a - b * Math.floor(a / b); return a < 0 ? a + b : a; }
    private static double dsin(double d) { return Math.sin(Math.toRadians(d)); }
    private static double dcos(double d) { return Math.cos(Math.toRadians(d)); }
    private static double darcsin(double x) { return Math.toDegrees(Math.asin(x)); }
    private static double darccos(double x) { return Math.toDegrees(Math.acos(x)); }
    private static double darctan2(double y, double x) { return Math.toDegrees(Math.atan2(y, x)); }

    private PrayerTimes() {}
}
