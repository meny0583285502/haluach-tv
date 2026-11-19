package com.meny.haluachtv;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * שירות נגישות: בודק כל הזמן מה מוצג במסך.
 * כל דבר שאינו הלוח (הגדרות, חנות, חלונית צד, כניסה לחשבון...) - מוחזר מיד ללוח.
 */
public class GuardService extends AccessibilityService {

    // חלונות מערכת שמותר להם להופיע (אישור ניפוי באגים, התקנה, הרשאות, ווליום)
    private final Set<String> allowed = new HashSet<>(Arrays.asList(
            "android", "com.android.systemui",
            "com.android.packageinstaller", "com.google.android.packageinstaller",
            "com.android.permissioncontroller", "com.google.android.permissioncontroller"));

    private final Handler h = new Handler(Looper.getMainLooper());
    private String lastPkg = "";
    private long lastLaunch = 0;

    private final Runnable tick = new Runnable() {
        @Override public void run() { check(); h.postDelayed(this, 2000); }
    };

    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (Prefs.pauseUntil(c) == Prefs.UNTIL_SCREEN_ON) Prefs.setPause(c, 0);
            h.postDelayed(GuardService.this::bringFront, 1000);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        allowed.add(getPackageName());
        // המקלדות שעל המסך - מותרות
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            for (InputMethodInfo i : imm.getEnabledInputMethodList()) allowed.add(i.getPackageName());
        } catch (Exception ignored) { }
        try { registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON)); } catch (Exception ignored) { }
        h.postDelayed(this::bringFront, 1500);
        h.postDelayed(tick, 2000);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        int t = e.getEventType();
        if (t != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && t != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return;
        CharSequence p = e.getPackageName();
        if (p != null) lastPkg = p.toString();
        h.removeCallbacks(quick);
        h.postDelayed(quick, 250);
    }

    private final Runnable quick = this::check;

    /** איזו אפליקציה מחזיקה כרגע את המסך */
    private String activePkg() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null && root.getPackageName() != null) {
                String s = root.getPackageName().toString();
                root.recycle();
                return s;
            }
        } catch (Exception ignored) { }
        return lastPkg;
    }

    private void check() {
        if (!Prefs.hasPassword(this) || Prefs.paused(this)) return;
        String pkg = activePkg();
        if (pkg == null || pkg.isEmpty() || allowed.contains(pkg)) return;
        bringFront();
    }

    private void bringFront() {
        if (!Prefs.hasPassword(this) || Prefs.paused(this)) return;
        long now = System.currentTimeMillis();
        if (now - lastLaunch < 700) return;
        lastLaunch = now;
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try { startActivity(i); } catch (Exception ignored) { }
        // חלונית צד/תפריט שלא נסגר ממעבר אפליקציה - סוגרים עם "חזור"
        h.postDelayed(() -> {
            String p = activePkg();
            if (p != null && !p.isEmpty() && !allowed.contains(p)) performGlobalAction(GLOBAL_ACTION_BACK);
        }, 600);
    }

    @Override public void onInterrupt() { }

    @Override
    public void onDestroy() {
        h.removeCallbacksAndMessages(null);
        try { unregisterReceiver(screenOn); } catch (Exception ignored) { }
        super.onDestroy();
    }
}
