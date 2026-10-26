package com.camon18i.voltage;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Keeps the launcher alive: if the app ever crashes, the error is saved and the launcher
 * comes straight back. A crash loop guard stops it from restarting forever.
 */
public class FegApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        try { Freezer.get(this).register(); } catch (Throwable ignored) {}   // freezes awake apps again when the screen turns off
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread t, Throwable e) {
                try {
                    Log.e("FEG", "crash on " + t.getName(), e);
                    SharedPreferences sp = getSharedPreferences("voltage_crash", MODE_PRIVATE);
                    long now = System.currentTimeMillis();
                    long first = sp.getLong("first", 0);
                    int n = sp.getInt("n", 0);
                    if (now - first > 60000) { first = now; n = 0; }
                    n++;
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    sp.edit().putLong("first", first).putInt("n", n).putLong("at", now)
                      .putString("last", sw.toString()).commit();
                    if (n <= 3) {   // at most 3 quick restarts in a minute
                        Intent i = new Intent(FegApp.this, MainActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        PendingIntent pi = PendingIntent.getActivity(FegApp.this, 7, i,
                                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                        if (am != null) am.set(AlarmManager.RTC, now + 800, pi);
                    }
                } catch (Throwable ignored) {}
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        });
    }
}
