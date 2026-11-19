package com.meny.haluachtv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends Activity {

    private WebView web;
    private final Handler h = new Handler(Looper.getMainLooper());
    private int backCount = 0;
    private long backFirst = 0;
    private boolean offline = false;

    private boolean triedCache = false;
    private ConnectivityManager.NetworkCallback netCb;

    private final Runnable retry = new Runnable() {
        @Override public void run() { load(); }
    };

    // בודק כל דקה אם האינטרנט חזר (בלי לרענן סתם את מה שמוצג)
    private final Runnable netCheck = new Runnable() {
        @Override public void run() {
            if (!triedCache) return;
            if (online()) load(); else h.postDelayed(this, 60000);
        }
    };

    private boolean online() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Exception e) { return true; }
    }

    /** טעינה חכמה: עם אינטרנט - גרסה עדכנית; בלי אינטרנט - מהזיכרון השמור. */
    private void load() {
        if (web == null) return;
        offline = false;
        triedCache = !online();
        web.getSettings().setCacheMode(triedCache
                ? WebSettings.LOAD_CACHE_ELSE_NETWORK : WebSettings.LOAD_DEFAULT);
        web.loadUrl(Prefs.url(this));
    }

    /** מהאתר אפשר לקרוא: HaluachTV.exit() או HaluachTV.menu() */
    public class Bridge {
        @JavascriptInterface public void exit() {
            h.post(() -> leave(Prefs.UNTIL_SCREEN_ON, false));
        }
        @JavascriptInterface public void exitMinutes(int minutes) {
            h.post(() -> leave(System.currentTimeMillis() + Math.max(1, minutes) * 60_000L, false));
        }
        @JavascriptInterface public void menu() { h.post(() -> adminMenu()); }
        @JavascriptInterface public boolean isApp() { return true; }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        if (!Prefs.hasPassword(this)) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        web.addJavascriptInterface(new Bridge(), "HaluachTV");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) onMainError();
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView v, int code, String desc, String url) {
                if (Build.VERSION.SDK_INT < 23) onMainError();
            }
        });

        load();

        // כשהאינטרנט חוזר - מרעננים לגרסה העדכנית
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                netCb = new ConnectivityManager.NetworkCallback() {
                    @Override public void onAvailable(Network n) {
                        h.post(() -> { if (offline || triedCache) { h.removeCallbacks(retry); h.postDelayed(retry, 3000); } });
                    }
                };
                ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE)).registerDefaultNetworkCallback(netCb);
            } catch (Exception ignored) { }
        }
    }

    private void onMainError() {
        if (!triedCache) {
            // אין חיבור - מציגים את מה שנשמר מהפעם הקודמת
            triedCache = true;
            web.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ONLY);
            h.post(() -> web.loadUrl(Prefs.url(this)));
            h.removeCallbacks(netCheck);
            h.postDelayed(netCheck, 60000);
            return;
        }
        showOffline();
    }

    private void showOffline() {
        if (offline) { h.removeCallbacks(retry); h.postDelayed(retry, 15000); return; }
        offline = true;
        String html = "<html><body style='background:#000;color:#ddd;font-family:sans-serif;"
                + "display:flex;align-items:center;justify-content:center;height:100vh;margin:0;direction:rtl'>"
                + "<div style='text-align:center;font-size:4vw'>אין חיבור לאינטרנט<br>"
                + "<span style='font-size:2.5vw;color:#888'>מנסה שוב אוטומטית...</span></div></body></html>";
        web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
        h.removeCallbacks(retry);
        h.postDelayed(retry, 15000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideUi();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideUi();
    }

    private void hideUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE) {
            if (e.getAction() == KeyEvent.ACTION_UP) {
                // לחיצה ארוכה (2.5 שניות) על "חזור" = חלון סיסמה
                if (e.getEventTime() - e.getDownTime() >= 2500) askPassword();
                else onBackKey();
            }
            return true;
        }
        // נעילת עוצמת הקול - כפתורי הווליום בשלט לא עושים כלום
        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN
                || k == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return true;
        }
        if (k == KeyEvent.KEYCODE_MENU || k == KeyEvent.KEYCODE_SETTINGS) {
            if (e.getAction() == KeyEvent.ACTION_UP) askPassword();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    // 5 לחיצות על "חזור" תוך 3 שניות = חלון סיסמה
    private void onBackKey() {
        long now = System.currentTimeMillis();
        if (now - backFirst > 3000) { backFirst = now; backCount = 0; }
        backCount++;
        if (backCount >= 5) { backCount = 0; askPassword(); return; }
        if (web != null && !offline && web.canGoBack()) web.goBack();
    }

    @Override
    public void onBackPressed() { /* חסום */ }

    private EditText numberField(boolean secret) {
        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER
                | (secret ? InputType.TYPE_NUMBER_VARIATION_PASSWORD : 0));
        return et;
    }

    private void askPassword() {
        final EditText et = numberField(true);
        new AlertDialog.Builder(this)
                .setTitle("סיסמת מנהל")
                .setView(et)
                .setPositiveButton("אישור", (d, w) -> {
                    if (Prefs.checkPassword(this, et.getText().toString())) adminMenu();
                    else Toast.makeText(this, "סיסמה שגויה", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("ביטול", null)
                .show();
    }

    private void adminMenu() {
        String[] items = {
                "יציאה ל-30 דקות",
                "יציאה עד שהטלוויזיה תכבה ותידלק",
                "רענון האתר",
                "שינוי כתובת האתר",
                "שינוי סיסמה",
                "הגדרות נגישות (הפעלת הנעילה)",
                "הגדרות הטלוויזיה (10 דקות)"
        };
        new AlertDialog.Builder(this)
                .setTitle("תפריט מנהל")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: leave(System.currentTimeMillis() + 30 * 60_000L, false); break;
                        case 1: leave(Prefs.UNTIL_SCREEN_ON, false); break;
                        case 2: load(); break;
                        case 3: changeUrl(); break;
                        case 4: changePassword(); break;
                        case 5:
                            Prefs.setPause(this, System.currentTimeMillis() + 10 * 60_000L);
                            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                            break;
                        case 6: leave(System.currentTimeMillis() + 10 * 60_000L, true); break;
                    }
                })
                .setNegativeButton("סגור", null)
                .show();
    }

    private void leave(long until, boolean settings) {
        Prefs.setPause(this, until);
        Intent i = settings
                ? new Intent(Settings.ACTION_SETTINGS)
                : new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception e) { moveTaskToBack(true); }
    }

    private void changeUrl() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        et.setText(Prefs.url(this));
        new AlertDialog.Builder(this)
                .setTitle("כתובת האתר")
                .setView(et)
                .setPositiveButton("שמור", (d, w) -> {
                    String u = et.getText().toString().trim();
                    if (u.length() > 3) { Prefs.setUrl(this, u); load(); }
                })
                .setNegativeButton("ביטול", null)
                .show();
    }

    private void changePassword() {
        final EditText et = numberField(true);
        new AlertDialog.Builder(this)
                .setTitle("סיסמה חדשה (לפחות 4 ספרות)")
                .setView(et)
                .setPositiveButton("שמור", (d, w) -> {
                    String pw = et.getText().toString();
                    if (pw.length() >= 4) {
                        Prefs.setPassword(this, pw);
                        Toast.makeText(this, "הסיסמה נשמרה", Toast.LENGTH_SHORT).show();
                    } else Toast.makeText(this, "קצרה מדי", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("ביטול", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        if (netCb != null) try {
            ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE)).unregisterNetworkCallback(netCb);
        } catch (Exception ignored) { }
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
