package com.meny.haluachtv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * פקודות מהמחשב (רק דרך ADB - מוגן בהרשאת DUMP שאין לאפליקציות רגילות).
 * cmd=exit  - יציאה מהלוח עד שהטלוויזיה תכבה ותידלק
 * cmd=lock  - חזרה לנעילה מיד
 * cmd=setpw --es pw 1234 - קביעת סיסמה חדשה (גם בלי לדעת את הקודמת, וגם כסיסמה ראשונה)
 */
public class CmdReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String cmd = i.getStringExtra("cmd");
        if ("exit".equals(cmd)) {
            Prefs.setPause(c, Prefs.UNTIL_SCREEN_ON);
        } else if ("lock".equals(cmd)) {
            Prefs.setPause(c, 0);
            try {
                c.startActivity(new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) { }
        } else if ("setpw".equals(cmd)) {
            String pw = i.getStringExtra("pw");
            if (pw != null && pw.matches("\\d{4,12}")) {
                Prefs.setPassword(c, pw);
                setResultData("ok:setpw:done");
            } else {
                setResultData("ok:setpw:bad");
            }
            return;
        }
        setResultData("ok:" + cmd);
    }
}
