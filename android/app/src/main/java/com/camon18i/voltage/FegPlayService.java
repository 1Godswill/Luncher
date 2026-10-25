package com.camon18i.voltage;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * Keeps FEG-PLAY music playing when Voltage is not on screen and shows the notification with
 * previous / play-pause / next. All the real work is done by FegEngine.
 */
public class FegPlayService extends Service {
    static final String A_PLAY = "com.camon18i.voltage.FEG_PLAY";
    static final String A_PAUSE = "com.camon18i.voltage.FEG_PAUSE";
    static final String A_NEXT = "com.camon18i.voltage.FEG_NEXT";
    static final String A_PREV = "com.camon18i.voltage.FEG_PREV";
    static final String A_STOP = "com.camon18i.voltage.FEG_STOP";
    private static final int NID = 4711;

    static FegPlayService instance;
    private boolean fg;

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent i, int flags, int startId) {
        FegEngine e = FegEngine.get(this);
        String a = i == null ? null : i.getAction();
        if (A_STOP.equals(a)) {
            e.pause();
            shutDown();
            return START_NOT_STICKY;
        }
        if (A_PLAY.equals(a)) e.play();
        else if (A_PAUSE.equals(a)) e.pause();
        else if (A_NEXT.equals(a)) e.next();
        else if (A_PREV.equals(a)) e.prev();
        refresh(true);
        return START_NOT_STICKY;
    }

    /** first = the service was just started: it must call startForeground right away. */
    void refresh(boolean first) {
        FegEngine e = FegEngine.get(this);
        Notification n;
        try { n = e.buildNotification(this); } catch (Exception ex) { if (first) shutDown(); return; }
        boolean playing = e.isPlaying();
        try {
            if (playing || first) {
                if (Build.VERSION.SDK_INT >= 29) startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
                else startForeground(NID, n);
                fg = true;
            }
            if (!playing) {
                if (fg) { stopForeground(false); fg = false; }   // paused: the notification stays but can be swiped away
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) nm.notify(NID, n);
            }
        } catch (Exception ex) {
            // the manifest is missing the FEG-PLAY service lines (see FEG-PLAY-SETUP.md): music still plays, just no notification
            shutDown();
        }
    }

    private void shutDown() {
        try { stopForeground(true); } catch (Exception ignored) {}
        fg = false;
        stopSelf();
    }

    @Override public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }
}
