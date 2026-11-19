package com.meny.haluachtv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * פקודות מהמחשב (רק דרך ADB - מוגן בהרשאת DUMP שאין לאפליקציות רגילות).
 * cmd=exit  - יציאה מהלוח עד שהטלוויזיה תכבה ותידלק
 * cmd=lock  - חזרה לנעילה מיד
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
        }
        setResultData("ok:" + cmd);
    }
}
