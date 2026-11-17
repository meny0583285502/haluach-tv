package com.meny.haluachtv;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

/** שירות נגישות: כל פעם שנפתח מסך אחר - מחזיר את הלוח לקדמת המסך. */
public class GuardService extends AccessibilityService {

    private final Handler h = new Handler(Looper.getMainLooper());
    private String lastPkg = "";
    private long lastLaunch = 0;

    private final Runnable tick = new Runnable() {
        @Override public void run() { check(); h.postDelayed(this, 20000); }
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
        try { registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON)); } catch (Exception ignored) { }
        h.postDelayed(this::bringFront, 1500);
        h.postDelayed(tick, 20000);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence p = e.getPackageName(), c = e.getClassName();
        if (p == null) return;
        String pkg = p.toString();
        if (pkg.equals(getPackageName())) { lastPkg = pkg; return; }
        // מתעלמים ממקלדת, חלוניות עוצמה וכו' - רק מסך מלא של אפליקציה אחרת
        if (c == null || !isActivity(pkg, c.toString())) return;
        lastPkg = pkg;
        h.postDelayed(this::check, 300);
    }

    private boolean isActivity(String pkg, String cls) {
        try {
            getPackageManager().getActivityInfo(new ComponentName(pkg, cls), 0);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private void check() {
        if (getPackageName().equals(lastPkg)) return;
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
    }

    @Override public void onInterrupt() { }

    @Override
    public void onDestroy() {
        h.removeCallbacksAndMessages(null);
        try { unregisterReceiver(screenOn); } catch (Exception ignored) { }
        super.onDestroy();
    }
}
