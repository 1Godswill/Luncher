package com.camon18i.voltage;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.HashMap;
import java.util.Map;

/** Counts active notifications per app so the launcher can draw badges. Needs notification access. */
public class BadgeService extends NotificationListenerService {

    /** package name -> number of active, non-ongoing notifications. Replaced as a whole, never edited. */
    static volatile Map<String, Integer> counts = new HashMap<>();
    static volatile boolean connected = false;
    static volatile Runnable onChange;

    @Override public void onListenerConnected() { connected = true; recount(); }

    @Override public void onListenerDisconnected() {
        connected = false;
        counts = new HashMap<>();
        fire();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) { recount(); }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) { recount(); }

    private void recount() {
        Map<String, Integer> fresh = new HashMap<>();
        try {
            StatusBarNotification[] list = getActiveNotifications();
            if (list != null) {
                for (StatusBarNotification sbn : list) {
                    Notification n = sbn.getNotification();
                    if (n == null) continue;
                    if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
                    if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0) continue;
                    String p = sbn.getPackageName();
                    Integer c = fresh.get(p);
                    fresh.put(p, c == null ? 1 : c + 1);
                }
            }
        } catch (Exception ignored) {}
        if (!fresh.equals(counts)) {
            counts = fresh;
            fire();
        }
    }

    private static void fire() {
        Runnable r = onChange;
        if (r != null) r.run();
    }
}
