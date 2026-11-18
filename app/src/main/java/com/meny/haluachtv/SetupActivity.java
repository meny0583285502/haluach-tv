package com.meny.haluachtv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** הגדרה ראשונה: כתובת, סיסמה, והפעלת הנעילה בנגישות. */
public class SetupActivity extends Activity {

    private LinearLayout step2;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int pad = dp(32);
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        sv.addView(root);

        root.addView(text("הגדרה ראשונה - הלוח", 26));
        root.addView(text("הסיסמה משמשת ליציאה מהלוח: לוחצים 5 פעמים מהר על \"חזור\" בשלט.", 17));

        root.addView(text("בחר סיסמת מנהל (מספרים בלבד, לפחות 4):", 18));
        final EditText pw1 = num();
        root.addView(pw1);
        root.addView(text("הקלד את הסיסמה שוב:", 18));
        final EditText pw2 = num();
        root.addView(pw2);

        Button save = new Button(this);
        save.setText("שמור והמשך");
        root.addView(save);

        step2 = new LinearLayout(this);
        step2.setOrientation(LinearLayout.VERTICAL);
        step2.setVisibility(Prefs.hasPassword(this) ? View.VISIBLE : View.GONE);
        step2.addView(text("\nשלב אחרון - נעילה:\n"
                + "לחץ על הכפתור למטה. במסך שנפתח בחר \"הלוח - נעילת מסך\" והפעל אותו.\n"
                + "מיד אחרי ההפעלה הלוח יעלה במסך מלא.\n\n"
                + "אם האפשרות אפורה ולא נותנת להפעיל: הגדרות ← אפליקציות ← הלוח ← "
                + "\"אפשר הגדרות מוגבלות\", ואז נסה שוב.", 17));
        Button acc = new Button(this);
        acc.setText("פתח הגדרות נגישות");
        acc.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        step2.addView(acc);
        Button skip = new Button(this);
        skip.setText("התחל בלי נעילה (לא מומלץ)");
        skip.setOnClickListener(v -> goMain());
        step2.addView(skip);
        root.addView(step2);

        save.setOnClickListener(v -> {
            String a = pw1.getText().toString(), c = pw2.getText().toString();
            if (a.length() < 4) { toast("הסיסמה קצרה מדי"); return; }
            if (!a.equals(c)) { toast("הסיסמאות לא זהות"); return; }
            Prefs.setPassword(this, a);
            if (Prefs.guardEnabled(this)) { goMain(); return; }
            step2.setVisibility(View.VISIBLE);
            acc.requestFocus();
        });

        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Prefs.hasPassword(this) && Prefs.guardEnabled(this)) goMain();
    }

    private void goMain() {
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    private EditText num() {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        return e;
    }

    private TextView text(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setPadding(0, dp(8), 0, dp(4));
        return t;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
