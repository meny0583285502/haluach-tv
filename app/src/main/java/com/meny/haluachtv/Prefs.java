package com.meny.haluachtv;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import java.security.MessageDigest;

final class Prefs {
    static final String DEFAULT_URL = "https://haluach.pages.dev/";
    static final long UNTIL_SCREEN_ON = Long.MAX_VALUE;

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
    }

    static String url(Context c) { return p(c).getString("url", DEFAULT_URL); }

    static void setUrl(Context c, String u) {
        u = u.trim();
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
        p(c).edit().putString("url", u).commit();
    }

    static boolean hasPassword(Context c) { return p(c).contains("pw"); }

    static void setPassword(Context c, String pw) { p(c).edit().putString("pw", hash(pw)).commit(); }

    static boolean checkPassword(Context c, String pw) {
        return hash(pw).equals(p(c).getString("pw", "-"));
    }

    static long pauseUntil(Context c) { return p(c).getLong("pause", 0); }

    static void setPause(Context c, long until) { p(c).edit().putLong("pause", until).commit(); }

    static boolean paused(Context c) { return System.currentTimeMillis() < pauseUntil(c); }

    static boolean guardEnabled(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(c.getPackageName() + "/");
    }

    private static String hash(String pw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(("haluach:" + pw).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return pw;
        }
    }
}
