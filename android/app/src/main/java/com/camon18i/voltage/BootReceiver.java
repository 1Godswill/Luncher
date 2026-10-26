package com.camon18i.voltage;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Starts FEG Launcher when the phone finishes turning on. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        String a = i == null ? null : i.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) return;
        try {
            Intent h = new Intent(c, MainActivity.class);
            h.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(h);   // Android 10+ only allows this if "Display over other apps" is on; otherwise the default-home setting starts us
        } catch (Throwable ignored) {}
    }
}
