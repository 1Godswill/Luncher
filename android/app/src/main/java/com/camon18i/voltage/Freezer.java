package com.camon18i.voltage;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * App freezer engine (v4.0).
 *
 * Freezing = turning an app off with "pm disable-user" so it cannot run, show notifications or use battery.
 * Android only lets the shell or root do that, so there are three ways to freeze:
 *   Shizuku  (no PC needed on Android 11+: start Shizuku with wireless debugging, then allow FEG Launcher)
 *   Root     (su)
 *   Soft     (fallback: the app is hidden from the drawer and its background processes are stopped, but it is not disabled)
 *
 * The list of frozen apps is kept in its own file ("voltage_freezer"), NOT in the launcher settings, so
 * "Reset" and "Restore backup" can never make the launcher forget which apps are disabled.
 *
 * "Launch from the freezer": a disabled app cannot start, so it is switched on for a moment, started, and
 * switched off again automatically (when the screen turns off, or when you are back on the home screen).
 * It stays in the frozen list the whole time.
 */
final class Freezer {

    static final int MODE_AUTO = 0, MODE_SHIZUKU = 1, MODE_ROOT = 2, MODE_SOFT = 3;
    static final int B_NONE = 0, B_SHIZUKU = 1, B_ROOT = 2, B_SOFT = 3;
    static final int REQ_SHIZUKU = 8101;

    /** Result callback, always delivered on the main thread. */
    interface Done { void done(boolean ok, String msg); }

    private static final Set<String> PROTECTED = new HashSet<>(Arrays.asList(
            "android", "com.android.systemui", "com.android.settings", "com.android.phone",
            "com.android.server.telecom", "com.android.dialer", "com.google.android.dialer", "com.android.incallui",
            "com.google.android.gms", "com.google.android.gsf", "com.android.providers.settings",
            "com.android.permissioncontroller", "com.google.android.permissioncontroller",
            "com.android.packageinstaller", "com.google.android.packageinstaller", "moe.shizuku.privileged.api"));

    private static Freezer inst;

    static synchronized Freezer get(Context c) {
        if (inst == null) inst = new Freezer(c.getApplicationContext());
        return inst;
    }

    private final Context ctx;
    private final PackageManager pm;
    private final SharedPreferences store, cfg;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    /** package -> {label, launcher key "pkg/class", "0" real freeze or "1" soft freeze} */
    private final LinkedHashMap<String, String[]> rec = new LinkedHashMap<>();
    private final Set<String> thawed = new HashSet<>();   // frozen apps that are switched on for now (will be frozen again)
    private volatile boolean rootChecked, rootOk;
    private boolean registered;

    private Freezer(Context c) {
        ctx = c;
        pm = c.getPackageManager();
        store = c.getSharedPreferences("voltage_freezer", Context.MODE_PRIVATE);
        cfg = c.getSharedPreferences("voltage", Context.MODE_PRIVATE);
        load();
    }

    // ================================================================ stored list

    private synchronized void load() {
        rec.clear();
        thawed.clear();
        try {
            JSONObject o = new JSONObject(store.getString("records", "{}"));
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String p = it.next();
                JSONObject r = o.getJSONObject(p);
                rec.put(p, new String[]{r.optString("l", p), r.optString("k", ""), r.optInt("m", 0) == 1 ? "1" : "0"});
            }
        } catch (Exception ignored) {}
        for (String s : store.getString("thawed", "").split("\n")) if (s.length() > 0) thawed.add(s);
    }

    private synchronized void save() {
        JSONObject o = new JSONObject();
        try {
            for (Map.Entry<String, String[]> e : rec.entrySet()) {
                JSONObject r = new JSONObject();
                r.put("l", e.getValue()[0]);
                r.put("k", e.getValue()[1]);
                r.put("m", "1".equals(e.getValue()[2]) ? 1 : 0);
                o.put(e.getKey(), r);
            }
        } catch (JSONException ignored) {}
        StringBuilder sb = new StringBuilder();
        for (String s : thawed) { if (sb.length() > 0) sb.append('\n'); sb.append(s); }
        store.edit().putString("records", o.toString()).putString("thawed", sb.toString()).commit();
    }

    synchronized boolean isFrozen(String pkg) { return rec.containsKey(pkg); }

    synchronized boolean isSoft(String pkg) {
        String[] r = rec.get(pkg);
        return r != null && "1".equals(r[2]);
    }

    synchronized boolean isThawed(String pkg) { return thawed.contains(pkg); }

    synchronized int thawedCount() { return thawed.size(); }

    synchronized int count() { return rec.size(); }

    /** One entry per frozen app: {package, label, launcher key}. */
    synchronized List<String[]> records() {
        List<String[]> l = new ArrayList<>();
        for (Map.Entry<String, String[]> e : rec.entrySet()) l.add(new String[]{e.getKey(), e.getValue()[0], e.getValue()[1]});
        return l;
    }

    private synchronized List<String> thawedList() { return new ArrayList<>(thawed); }

    private synchronized void put(String pkg, String[] t, boolean soft) {
        rec.put(pkg, new String[]{t[1], t[2], soft ? "1" : "0"});
        thawed.remove(pkg);
        save();
    }

    private synchronized void addThawed(String pkg) { if (thawed.add(pkg)) save(); }

    private synchronized void removeThawed(String pkg) { if (thawed.remove(pkg)) save(); }

    private synchronized void drop(String pkg) {
        boolean a = rec.remove(pkg) != null;
        boolean b = thawed.remove(pkg);
        if (a || b) save();
        try { iconFile(pkg).delete(); } catch (Exception ignored) {}
    }

    /** The app was uninstalled: forget it. */
    void forget(String pkg) { if (pkg != null) drop(pkg); }

    /**
     * Drops entries of apps that were switched on from outside (for example in Android Settings).
     * Returns true when something changed (the launcher should then reload).
     */
    synchronized boolean sync() {
        boolean changed = false;
        for (Iterator<Map.Entry<String, String[]>> it = rec.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, String[]> e = it.next();
            String p = e.getKey();
            if ("1".equals(e.getValue()[2])) continue;           // soft freeze: the app is always switched on
            if (thawed.contains(p)) continue;
            if (installed(p) && !isDisabled(p)) { it.remove(); changed = true; try { iconFile(p).delete(); } catch (Exception ignored) {} }
        }
        if (changed) save();
        return changed;
    }

    // ================================================================ settings

    int mode() { return Math.max(0, Math.min(3, cfg.getInt("freeze_mode", MODE_AUTO))); }

    /** 0 = freeze again when the screen turns off, 1 = also when you are back on the home screen for a while. */
    int rule() { return cfg.getInt("freeze_refreeze", 0) == 1 ? 1 : 0; }

    // ================================================================ app state

    private boolean installed(String pkg) {
        try { pm.getApplicationInfo(pkg, 0); return true; } catch (Throwable t) { return false; }
    }

    boolean isDisabled(String pkg) {
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            if (!ai.enabled) return true;
        } catch (Throwable ignored) {}
        try {
            int s = pm.getApplicationEnabledSetting(pkg);
            return s == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER || s == PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        } catch (Throwable t) { return false; }
    }

    /** True when starting this app first needs it switched on. */
    boolean needsThaw(String pkg) { return isFrozen(pkg) && !isSoft(pkg) && isDisabled(pkg); }

    /** Apps that must never be frozen: this launcher, the phone's core apps, home screens and the keyboard. */
    Set<String> protectedSet() {
        Set<String> s = new HashSet<>(PROTECTED);
        s.add(ctx.getPackageName());
        try {
            Intent h = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            for (ResolveInfo ri : pm.queryIntentActivities(h, 0)) if (ri.activityInfo != null) s.add(ri.activityInfo.packageName);
        } catch (Throwable ignored) {}
        try {
            String ime = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            if (ime != null && ime.indexOf('/') > 0) s.add(ime.substring(0, ime.indexOf('/')));
        } catch (Throwable ignored) {}
        return s;
    }

    boolean isProtected(String pkg) { return protectedSet().contains(pkg); }

    boolean isSystemApp(String pkg) {
        try { return (pm.getApplicationInfo(pkg, 0).flags & ApplicationInfo.FLAG_SYSTEM) != 0; } catch (Throwable t) { return false; }
    }

    // ================================================================ icons

    private File iconFile(String pkg) {
        File d = new File(ctx.getFilesDir(), "frz_icons");
        d.mkdirs();
        return new File(d, pkg + ".png");
    }

    private void saveIcon(String pkg) {
        try {
            Drawable d = pm.getApplicationIcon(pkg);
            Bitmap b = Bitmap.createBitmap(144, 144, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, 144, 144);
            d.draw(c);
            FileOutputStream fo = new FileOutputStream(iconFile(pkg));
            b.compress(Bitmap.CompressFormat.PNG, 100, fo);
            fo.close();
        } catch (Throwable ignored) {}
    }

    /** The app's icon, also for apps that are switched off (falls back to a copy saved when it was frozen). */
    Drawable iconDrawable(String pkg) {
        try { return pm.getApplicationIcon(pkg); } catch (Throwable ignored) {}
        try {
            File f = iconFile(pkg);
            if (f.exists()) {
                Bitmap b = BitmapFactory.decodeFile(f.getPath());
                if (b != null) return new BitmapDrawable(ctx.getResources(), b);
            }
        } catch (Throwable ignored) {}
        return pm.getDefaultActivityIcon();
    }

    // ================================================================ Shizuku and root (reflection: nothing breaks if they are missing)

    private static Class<?> szClass() throws ClassNotFoundException { return Class.forName("rikka.shizuku.Shizuku"); }

    static boolean shizukuRunning() {
        try { return Boolean.TRUE.equals(szClass().getMethod("pingBinder").invoke(null)); } catch (Throwable t) { return false; }
    }

    static boolean shizukuAllowed() {
        try {
            Object r = szClass().getMethod("checkSelfPermission").invoke(null);
            return r instanceof Integer && ((Integer) r) == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) { return false; }
    }

    /** Shows Shizuku's "Allow FEG Launcher?" pop-up. */
    static boolean shizukuAsk() {
        try { szClass().getMethod("requestPermission", int.class).invoke(null, REQ_SHIZUKU); return true; } catch (Throwable t) { return false; }
    }

    boolean shizukuInstalled() {
        try { pm.getPackageInfo("moe.shizuku.privileged.api", 0); return true; } catch (Throwable t) { return false; }
    }

    private static String read(InputStream in) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toString("UTF-8");
        } catch (Throwable t) { return ""; }
    }

    /** Waits for a process without Process.waitFor(timeout) (needs Android 8). Returns the exit code or -1. */
    private static int waitFor(Process p, long ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            try { return p.exitValue(); }
            catch (IllegalThreadStateException running) {
                try { Thread.sleep(120); } catch (InterruptedException e) { break; }
            }
        }
        try { p.destroy(); } catch (Throwable ignored) {}
        return -1;
    }

    /** Runs a shell script with Shizuku or root. Returns {exit code, output}. */
    private String[] sh(int backend, String script, long ms) {
        try {
            Process p;
            String full = "(" + script + ") 2>&1";
            if (backend == B_SHIZUKU) {
                Method m = szClass().getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
                m.setAccessible(true);
                p = (Process) m.invoke(null, new Object[]{new String[]{"sh", "-c", full}, null, null});
            } else {
                p = Runtime.getRuntime().exec(new String[]{"su", "-c", full});
            }
            int rc = waitFor(p, ms);
            String out = read(p.getInputStream());
            return new String[]{String.valueOf(rc), out};
        } catch (Throwable t) {
            if (t instanceof InvocationTargetException && t.getCause() != null) t = t.getCause();
            return new String[]{"-1", String.valueOf(t.getMessage() != null ? t.getMessage() : t.toString())};
        }
    }

    /** Worker thread only: asks for root once (the root manager may show a pop-up). */
    private void probeRoot() {
        if (rootChecked) return;
        rootChecked = true;
        rootOk = false;
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            int rc = waitFor(p, 25000);
            String out = read(p.getInputStream());
            rootOk = rc == 0 && out.contains("uid=0");
        } catch (Throwable t) { rootOk = false; }
    }

    /** Checks root again (after the user changed something), then calls back on the main thread. */
    void recheckRoot(final Runnable after) {
        io.execute(new Runnable() {
            @Override public void run() {
                rootChecked = false;
                probeRoot();
                if (after != null) ui.post(after);
            }
        });
    }

    /** Worker thread: which way to freeze with, following the chosen mode. */
    private int backend() {
        int m = mode();
        if (m == MODE_SOFT) return B_SOFT;
        boolean sz = shizukuRunning() && shizukuAllowed();
        if (m == MODE_SHIZUKU) return sz ? B_SHIZUKU : B_NONE;
        if (m == MODE_ROOT) { probeRoot(); return rootOk ? B_ROOT : B_NONE; }
        if (sz) return B_SHIZUKU;
        probeRoot();
        return rootOk ? B_ROOT : B_SOFT;
    }

    /** Worker thread: any way to switch apps on or off for real, whatever the mode says. */
    private int realBackend() {
        if (shizukuRunning() && shizukuAllowed()) return B_SHIZUKU;
        probeRoot();
        return rootOk ? B_ROOT : B_NONE;
    }

    /** For the screen: the way that would be used now, without asking for root. */
    int peekBackend() {
        int m = mode();
        if (m == MODE_SOFT) return B_SOFT;
        boolean sz = shizukuRunning() && shizukuAllowed();
        if (m == MODE_SHIZUKU) return sz ? B_SHIZUKU : B_NONE;
        if (m == MODE_ROOT) return rootChecked && rootOk ? B_ROOT : B_NONE;
        if (sz) return B_SHIZUKU;
        return rootChecked && rootOk ? B_ROOT : B_SOFT;
    }

    String status() {
        int b = peekBackend();
        if (b == B_SHIZUKU) return "Shizuku connected. Frozen apps are really switched off.";
        if (b == B_ROOT) return "Root found. Frozen apps are really switched off.";
        if (b == B_SOFT) {
            if (mode() == MODE_SOFT) return "Soft freeze (your choice): frozen apps are hidden from the drawer and stopped, but not switched off.";
            return "No Shizuku or root found, so freezing is soft: apps are hidden from the drawer and stopped, but not switched off. Tap Set up for real freezing.";
        }
        if (mode() == MODE_SHIZUKU)
            return shizukuRunning() ? "Shizuku is running, but FEG Launcher is not allowed yet. Tap Set up." : "Shizuku is not running. Tap Set up.";
        return "Root is not available. Tap Set up or pick another mode in FEG Launcher settings.";
    }

    private String noBackendMsg() {
        if (mode() == MODE_SHIZUKU)
            return shizukuRunning() ? "Allow FEG Launcher in Shizuku first (App freezer > Set up)." : "Shizuku is not running. Open Shizuku and tap Start.";
        return "Root is not available on this phone.";
    }

    private static String shorten(String s) {
        if (s == null) return "";
        s = s.trim();
        int nl = s.indexOf('\n');
        if (nl > 0) s = s.substring(0, nl);
        return s.length() > 120 ? s.substring(0, 120) : s;
    }

    private static boolean validPkg(String p) { return p != null && p.matches("[A-Za-z0-9_.]+"); }

    private void kill(String pkg) {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) am.killBackgroundProcesses(pkg);
        } catch (Throwable ignored) {}
    }

    private void post(final Done cb, final boolean ok, final String msg) {
        if (cb == null) return;
        ui.post(new Runnable() { @Override public void run() { cb.done(ok, msg); } });
    }

    // ================================================================ operations (all run in the background)

    /** Bulk freeze. targets: {package, label, launcher key}. */
    void freeze(final List<String[]> targets, final Done cb) {
        io.execute(new Runnable() {
            @Override public void run() {
                int b = backend();
                if (b == B_NONE) { post(cb, false, noBackendMsg()); return; }
                Set<String> prot = protectedSet();
                List<String[]> cand = new ArrayList<>();
                StringBuilder script = new StringBuilder();
                for (String[] t : targets) {
                    if (!validPkg(t[0]) || prot.contains(t[0])) continue;
                    saveIcon(t[0]);
                    cand.add(t);
                    if (b != B_SOFT) {
                        if (script.length() > 0) script.append("; ");
                        script.append("pm disable-user --user 0 ").append(t[0]);
                    }
                }
                if (cand.isEmpty()) { post(cb, false, "Nothing to freeze (these apps are protected)."); return; }
                String err = "";
                if (b != B_SOFT) err = sh(b, script.toString(), 90000)[1];
                int done = 0;
                for (String[] t : cand) {
                    if (b == B_SOFT) { kill(t[0]); put(t[0], t, true); done++; }
                    else if (isDisabled(t[0])) { put(t[0], t, false); done++; }
                }
                String what = done == 1 ? "1 app" : done + " apps";
                if (done == cand.size()) post(cb, true, "Froze " + what + (b == B_SOFT ? " (soft)" : ""));
                else post(cb, done > 0, "Froze " + done + " of " + cand.size() + ". " + shorten(err));
            }
        });
    }

    /** Bulk unfreeze (also used for "Unfreeze all"). */
    void unfreeze(final List<String> pkgs, final Done cb) {
        io.execute(new Runnable() {
            @Override public void run() {
                List<String> need = new ArrayList<>();
                for (String p : pkgs) if (validPkg(p) && isFrozen(p) && !isSoft(p) && isDisabled(p)) need.add(p);
                String err = "";
                if (!need.isEmpty()) {
                    int rb = realBackend();
                    if (rb != B_NONE) {
                        StringBuilder script = new StringBuilder();
                        for (String p : need) {
                            if (script.length() > 0) script.append("; ");
                            script.append("pm enable ").append(p);
                        }
                        err = sh(rb, script.toString(), 90000)[1];
                    }
                }
                int cleared = 0, stuck = 0;
                for (String p : pkgs) {
                    if (!isFrozen(p)) continue;
                    if (!isSoft(p) && isDisabled(p)) { stuck++; continue; }
                    drop(p);
                    cleared++;
                }
                String what = cleared == 1 ? "1 app" : cleared + " apps";
                if (stuck == 0) post(cb, true, "Unfroze " + what);
                else post(cb, cleared > 0, "Unfroze " + cleared + ". " + stuck + " could not be switched on: Shizuku or root is needed (or switch them on in Android Settings > Apps). " + shorten(err));
            }
        });
    }

    /** Switches a frozen app on so it can start. It stays in the frozen list and is frozen again later. */
    void thawForLaunch(final String pkg, final Done cb) {
        io.execute(new Runnable() {
            @Override public void run() {
                if (!isFrozen(pkg) || isSoft(pkg)) { post(cb, true, ""); return; }
                if (!isDisabled(pkg)) { addThawed(pkg); post(cb, true, ""); return; }
                int rb = realBackend();
                if (rb == B_NONE) { post(cb, false, "Can't wake this app: Shizuku or root is not available. Open App freezer > Set up."); return; }
                addThawed(pkg);   // written first, so it is frozen again even if the launcher is stopped half way
                sh(rb, "pm enable " + pkg, 30000);
                if (isDisabled(pkg)) { removeThawed(pkg); post(cb, false, "Couldn't switch the app on."); return; }
                post(cb, true, "");
            }
        });
    }

    /** Freezes every app that was switched on for a launch. */
    void refreezeThawed(final Done cb) {
        io.execute(new Runnable() {
            @Override public void run() {
                List<String> l = thawedList();
                if (l.isEmpty()) { post(cb, true, "Nothing to freeze again"); return; }
                List<String> real = new ArrayList<>();
                for (String p : l) {
                    if (validPkg(p) && isFrozen(p) && !isSoft(p)) real.add(p); else removeThawed(p);
                }
                if (real.isEmpty()) { post(cb, true, ""); return; }
                int rb = realBackend();
                if (rb == B_NONE) { post(cb, false, "Can't freeze again: Shizuku or root is not available."); return; }
                StringBuilder script = new StringBuilder();
                for (String p : real) {
                    if (script.length() > 0) script.append("; ");
                    script.append("pm disable-user --user 0 ").append(p);
                }
                sh(rb, script.toString(), 90000);
                int n = 0;
                for (String p : real) if (isDisabled(p)) { removeThawed(p); n++; }
                post(cb, n == real.size(), n == real.size() ? "Froze " + n + " again" : "Some apps could not be frozen again");
            }
        });
    }

    /** Freezes awake apps again whenever the screen turns off. Called once from FegApp. */
    synchronized void register() {
        if (registered) return;
        registered = true;
        ctx.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (thawedCount() > 0) refreezeThawed(null);
            }
        }, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }
}
