package com.camon18i.voltage;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Path;
import android.graphics.Outline;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.PowerManager;
import android.os.SystemClock;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Voltage Launcher: standalone home screen with clay icons, folders, widgets, drag and drop. */
public class MainActivity extends Activity {

    static class App {
        String label, pkg, cls, key, iconName;
        Bitmap plain;
        ComponentName cn;
        boolean isClock() { return "ic_clock_dyn".equals(iconName); }
        boolean isCal() { return "@cal".equals(iconName); }
    }

    static final int T_APP = 0, T_FOLDER = 1, T_WIDGET = 2;

    /** One thing on the home screen: an app, a folder or a widget. Laid out in order, first-fit. */
    static class Item {
        int type;
        String key;                              // app
        String name = "Folder";                  // folder
        List<String> keys = new ArrayList<>();   // folder
        int wid, w = 1, h = 1;                   // widget
        int page, row, col;                      // computed
    }

    private int COLS = 4, ROWS = 5; // home grid size (settings)
    private static final int REQ_BIND = 71, REQ_CONFIG = 72, REQ_PICK_WP = 73;
    private static final int[] DEFAULT_WALLPAPERS = { R.drawable.wallpaper_cross_1, R.drawable.wallpaper_cross_2 };

    private PackageManager pm;
    private SharedPreferences prefs;
    private final List<App> apps = new ArrayList<>();
    private final Map<String, App> byKey = new HashMap<>();
    private final Map<String, String> byComp = new HashMap<>();
    private final Map<String, String> byPkg = new HashMap<>();
    private final Map<String, String> kindPkg = new HashMap<>();
    private final Map<String, Bitmap> cache = new HashMap<>();
    private final Map<String, Bitmap> styledCache = new HashMap<>();
    private int iconStyle = IconStyler.CLAY;
    private int iconSizePct = 100;
    private SettingsPanel settings;
    private boolean showLabels = true, drawerEnabled = true, searchContacts = false, searchSettings = true;
    private int labelSp = 11, drawerCols = 4, drawerScroll = 0;
    // v3.4: widgets, dock, gestures, badges
    private boolean widgetsMenuBtn = true, widgetsLocked = false, dockShow = true, badgeDock = true, badgeFolders = true;
    private int widgetRadius = 0, widgetGap = 4, dockCount = 4, dockAlpha = 27, dockRadius = 30, badgeStyle = 0, swipeSens = 3;
    // v3.5: animations, folders, theme, 3D, lock, hide, backup, performance
    private int animOpen, animClose, animScroll, animFolder, animSpeed = 100, folderStyle, folderSize = 100, themeMode;
    private int fxDepth = 50, lockCount = 6, lockSpeed = 3, applockDelay, perfMode;
    private boolean fxShadows, fxParallax, fxWpScroll, fxFloat, lockOn, lockScreenOff = true, appLock, hideSearch = true;
    private boolean reduceAnim, autoSaver = true, trimMem = true;
    private boolean saverNow = false, resumed = false, awayFlag = false, lockPending = false, lockCancellable = false;
    private final Set<String> hiddenPkgs = new HashSet<>(), protectedPkgs = new HashSet<>();
    private final Map<String, Long> appUnlocked = new HashMap<>();
    private LockView lockView;
    private final List<View> floatViews = new ArrayList<>();
    private ValueAnimator floatAnim;
    private SensorManager sensors;
    private Sensor accel;
    private float tiltX, tiltY;
    private WallpaperManager wpm;
    private static final int REQ_BACKUP = 75, REQ_RESTORE = 76;
    private ScaleGestureDetector sgd;
    private float pinchTotal = 1f;
    private Runnable badgeListener;
    private final List<BadgeRef> badgeRefs = new ArrayList<>();
    static class BadgeRef { TextView v; List<String> pkgs; }
    static final String[] GESTURE_NAMES = {"Nothing", "App drawer", "Search", "Notifications", "Quick settings", "Voltage settings", "Home menu", "Open an app..."};
    interface AppPick { void on(App a); }
    private LinearLayout searchExtras, drawerPagesBox;
    private PagerHSV drawerPager;
    private Runnable firstExtra;
    private static final int REQ_CONTACTS = 74;
    private final List<App> dockApps = new ArrayList<>();
    private final List<App> shown = new ArrayList<>();
    private final List<Item> items = new ArrayList<>();
    private final List<View> widgetViews = new ArrayList<>();

    private FrameLayout root;
    private PagerHSV pager;
    private LinearLayout pagesBox, dots, dock, targetBar;
    private TextView tRemove, tInfo, tUninstall;
    private FrameLayout drawer;
    private GridView grid;
    private EditText search;
    private DrawerAdapter adapter;
    private boolean drawerOpen = false;
    private int screenW;
    private GestureDetector gd;

    private AppWidgetManager wm;
    private AppWidgetHost host;
    private int pendingWid = -1;

    // drag state
    private Item dragItem;
    private int dragIdx;
    private View dragView;
    private ImageView ghost;
    private float lastX, lastY, dragStartX, dragStartY;
    private boolean dragCancelSent;
    private int edgeDir = 0;

    private final Runnable rebuildRun = new Runnable() { public void run() { rebuildHome(); } };
    private final Runnable edgeRun = new Runnable() {
        public void run() {
            if (dragItem == null || edgeDir == 0) return;
            int np = pager.page() + edgeDir;
            if (np >= 0 && np < pager.count()) pager.goTo(np);
            root.postDelayed(this, 900);
        }
    };

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_PACKAGE_REMOVED.equals(i.getAction()) && i.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
            reloadAll();
        }
    };

    // ================================================================ lifecycle

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        pm = getPackageManager();
        prefs = getSharedPreferences("voltage", MODE_PRIVATE);
        screenW = getResources().getDisplayMetrics().widthPixels;
        wm = AppWidgetManager.getInstance(this);
        host = new AppWidgetHost(this, 1024);
        iconStyle = Math.max(0, Math.min(IconStyler.NAMES.length - 1, prefs.getInt("icon_style", IconStyler.CLAY)));
        iconSizePct = Math.max(SettingsPanel.SIZE_MIN, Math.min(SettingsPanel.SIZE_MAX, prefs.getInt("icon_size", 100)));
        showLabels = prefs.getBoolean("labels_show", true);
        labelSp = clampi(prefs.getInt("labels_size", 11), 9, 15);
        COLS = clampi(prefs.getInt("grid_cols", 4), 3, 6);
        ROWS = clampi(prefs.getInt("grid_rows", 5), 4, 7);
        drawerEnabled = prefs.getBoolean("drawer_on", true);
        drawerCols = clampi(prefs.getInt("drawer_cols", 4), 3, 6);
        drawerScroll = clampi(prefs.getInt("drawer_scroll", 0), 0, 2);
        searchContacts = prefs.getBoolean("search_contacts", false);
        searchSettings = prefs.getBoolean("search_settings", true);
        loadExtraPrefs();
        loadIconMap();
        buildUi();
        loadApps();
        rebuildHome();
        if (!prefs.getBoolean("wp_default_done", false)) {
            prefs.edit().putBoolean("wp_default_done", true).apply();
            useDefault(0); // first run: crimson cross wallpaper
        }

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f);
        IntentFilter sf = new IntentFilter();
        sf.addAction(Intent.ACTION_SCREEN_OFF);
        sf.addAction(Intent.ACTION_BATTERY_LOW);
        sf.addAction(Intent.ACTION_BATTERY_OKAY);
        sf.addAction(Intent.ACTION_POWER_CONNECTED);
        sf.addAction(Intent.ACTION_POWER_DISCONNECTED);
        sf.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        registerReceiver(screenReceiver, sf);
        sensors = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accel = sensors != null ? sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) : null;
        wpm = WallpaperManager.getInstance(this);
        lockPending = lockOn; // locked on a cold start

        badgeListener = new Runnable() {
            public void run() {
                if (root != null) root.post(new Runnable() { public void run() { refreshBadges(); } });
            }
        };
        BadgeService.onChange = badgeListener;

        gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null || dragItem != null) return false;
                float dy = e2.getY() - e1.getY(), dx = e2.getX() - e1.getX();
                float minV = 1400 - swipeSens * 200; // sensitivity 1..5 -> 1200..400 px/s
                if (Math.abs(dy) > Math.abs(dx) * 1.5f && Math.abs(vy) > minV) {
                    if (dy < 0 && !drawerOpen) { if (!startsInWidget(e1)) runGesture("gest_up", 1); }
                    else if (dy > 0 && drawerOpen && gridAtTop()) closeDrawer();
                    else if (dy > 0 && !drawerOpen) { if (!startsInWidget(e1)) runGesture("gest_down", 3); }
                }
                return false;
            }
            @Override public boolean onDoubleTap(MotionEvent e) {
                if (dragItem != null || drawerOpen || settingsOpen()) return false;
                if (!emptyHomeSpot(e)) return false;
                runGesture("gest_double", 0);
                return true;
            }
        });
        sgd = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector d) { pinchTotal = 1f; return true; }
            @Override public boolean onScale(ScaleGestureDetector d) { pinchTotal *= d.getScaleFactor(); return true; }
            @Override public void onScaleEnd(ScaleGestureDetector d) {
                if (dragItem != null || drawerOpen || settingsOpen()) return;
                if (pinchTotal < 0.8f) runGesture("gest_pinch_in", 0);
                else if (pinchTotal > 1.25f) runGesture("gest_pinch_out", 0);
            }
        });
    }

    @Override protected void onStart() {
        super.onStart();
        try { host.startListening(); } catch (Exception ignored) {}
    }

    @Override protected void onPause() {
        resumed = false;
        stopFx();
        super.onPause();
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (trimMem && level >= TRIM_MEMORY_UI_HIDDEN) { cache.clear(); styledCache.clear(); }
    }

    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        if (themeMode == Theme.AUTO) applyThemeNow();
    }

    @Override protected void onStop() {
        awayFlag = true;
        try { host.stopListening(); } catch (Exception ignored) {}
        super.onStop();
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(pkgReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(screenReceiver); } catch (Exception ignored) {}
        if (BadgeService.onChange == badgeListener) BadgeService.onChange = null;
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        rebuildHome(); // refreshes the calendar date
        if (settings != null) settings.refreshBadgeStatus();
        requestBadgeRebind();
        resumed = true;
        updateSaver();
        if (awayFlag) { awayFlag = false; animateReturn(); }
        startFx();
        applyScrollFx(pager.getScrollX());
        if (lockOn && lockPending && lockView == null) showMainLock();
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (settingsOpen()) close();
        if (dragItem != null) endDrag();
        if (drawerOpen) closeDrawer(); else pager.goTo(0);
    }

    @Override public void onBackPressed() {
        if (lockView != null) { if (lockCancellable) hideLock(); return; }
        if (settingsOpen()) { close(); return; }
        if (dragItem != null) { endDrag(); return; }
        if (drawerOpen) closeDrawer(); else pager.goTo(0);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent ev) {
        lastX = ev.getRawX();
        lastY = ev.getRawY();
        if (lockView != null) return super.dispatchTouchEvent(ev);
        if (dragItem != null) {
            if (!dragCancelSent) {
                MotionEvent c = MotionEvent.obtain(ev);
                c.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(c);
                c.recycle();
                dragCancelSent = true;
            }
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_MOVE: dragMove(); break;
                case MotionEvent.ACTION_UP: dragDrop(); break;
                case MotionEvent.ACTION_CANCEL: endDrag(); break;
                default: break;
            }
            return true;
        }
        if (gd != null && !settingsOpen()) gd.onTouchEvent(ev);
        if (sgd != null && !settingsOpen() && !drawerOpen) sgd.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_PICK_WP) {
            if (res == RESULT_OK && data != null && data.getData() != null) importWallpaper(data.getData());
            return;
        }
        if (req == REQ_BACKUP) { if (res == RESULT_OK && data != null && data.getData() != null) writeBackup(data.getData()); return; }
        if (req == REQ_RESTORE) { if (res == RESULT_OK && data != null && data.getData() != null) readRestore(data.getData()); return; }
        if (req != REQ_BIND && req != REQ_CONFIG) return;
        int id = pendingWid;
        if (data != null && data.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_ID))
            id = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        if (id < 0) return;
        if (res != RESULT_OK) { host.deleteAppWidgetId(id); pendingWid = -1; return; }
        AppWidgetProviderInfo info = wm.getAppWidgetInfo(id);
        if (info == null) { host.deleteAppWidgetId(id); pendingWid = -1; return; }
        if (req == REQ_BIND) afterBind(id, info); else addWidgetItem(id, info);
    }

    private void reloadAll() {
        loadApps();
        rebuildHome();
        if (adapter != null) filter(search.getText().toString());
    }

    // ================================================================ icon pack data

    private void loadIconMap() {
        try {
            android.content.res.XmlResourceParser p = getResources().getXml(R.xml.appfilter);
            int e = p.getEventType();
            while (e != XmlPullParser.END_DOCUMENT) {
                if (e == XmlPullParser.START_TAG) {
                    String n = p.getName();
                    String key = compKey(p.getAttributeValue(null, "component"));
                    if (key != null) {
                        String d = null;
                        if ("item".equals(n)) d = p.getAttributeValue(null, "drawable");
                        else if ("calendar".equals(n)) d = "@cal";
                        if (d != null) {
                            byComp.put(key, d);
                            String pk = key.substring(0, key.indexOf('/'));
                            if (!byPkg.containsKey(pk)) byPkg.put(pk, d);
                        }
                    }
                }
                e = p.next();
            }
        } catch (Exception ex) { /* icon map is optional */ }

        addKind(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR), "ic_calculator");
        addKind(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR), "@cal");
        addKind(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CONTACTS), "ic_contacts");
        addKind(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY), "ic_gallery");
        addKind(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS), "ic_maps");
        addKind(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC), "ic_music");
        addKind(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL), "ic_gmail");
        addKind(new Intent(Intent.ACTION_DIAL), "ic_phone");
        addKind(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")), "ic_messages");
        addKind(new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE), "ic_camera");
        addKind(new Intent(Settings.ACTION_SETTINGS), "ic_settings");
    }

    private void addKind(Intent i, String icon) {
        try {
            if (i.getSelector() != null) i = i.getSelector();
            ResolveInfo r = pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
            if (r != null && r.activityInfo != null && !"android".equals(r.activityInfo.packageName)) {
                String pk = r.activityInfo.packageName;
                if (!kindPkg.containsKey(pk)) kindPkg.put(pk, icon);
            }
        } catch (Exception ignored) {}
    }

    private static String compKey(String c) {
        if (c == null) return null;
        int a = c.indexOf('{'), z = c.lastIndexOf('}');
        if (a < 0 || z < 0) return null;
        ComponentName cn = ComponentName.unflattenFromString(c.substring(a + 1, z));
        return cn == null ? null : cn.getPackageName() + "/" + cn.getClassName();
    }

    private String iconFor(String pkg, String cls) {
        String d = byComp.get(pkg + "/" + cls);
        if (d == null) d = byPkg.get(pkg);
        if (d == null) d = kindPkg.get(pkg);
        if (d == null) {
            String l = pkg.toLowerCase();
            if (l.contains("deskclock")) d = "ic_clock_dyn";
            else if (l.contains("calculator")) d = "ic_calculator";
            else if (l.endsWith(".files") || l.contains("filemanager")) d = "ic_files";
        }
        if ("ic_clock".equals(d)) d = "ic_clock_dyn";
        return d;
    }

    // ================================================================ apps

    private void loadApps() {
        List<App> old = new ArrayList<>(apps);
        apps.clear(); byKey.clear(); cache.clear(); styledCache.clear();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
        for (ResolveInfo ri : list) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            App a = new App();
            a.pkg = pkg;
            a.cls = ri.activityInfo.name;
            a.key = pkg + "/" + a.cls;
            a.cn = new ComponentName(pkg, a.cls);
            CharSequence lab = ri.loadLabel(pm);
            a.label = lab == null ? pkg : lab.toString();
            a.iconName = iconFor(pkg, a.cls);
            if (a.iconName != null && !a.isCal() && !a.isClock()) {
                Bitmap bm = res(a.iconName);
                if (bm == null) a.iconName = null; else a.plain = styled(a.iconName, bm);
            }
            if (a.iconName == null) {
                a.plain = iconStyle == IconStyler.CLAY ? composeIcon(ri)
                        : IconStyler.tileFromDrawable(ri.loadIcon(pm), iconStyle);
            }
            apps.add(a);
            byKey.put(a.key, a);
        }
        Collections.sort(apps, new Comparator<App>() {
            @Override public int compare(App x, App y) { return x.label.compareToIgnoreCase(y.label); }
        });
        buildDock();
        if (apps.isEmpty() && !old.isEmpty()) return; // query failed: keep the saved layout untouched
        loadItems();
        if (!drawerEnabled) addAllToHome();
    }

    private Bitmap res(String name) {
        Bitmap c = cache.get(name);
        if (c != null) return c;
        int id = getResources().getIdentifier(name, "drawable", getPackageName());
        if (id == 0) return null;
        Bitmap b = BitmapFactory.decodeResource(getResources(), id);
        if (b != null) cache.put(name, b);
        return b;
    }

    /** Gives any app icon a clay / 3D feel: rounded body, puffy shading, thick slab and soft shadow. */
    private Bitmap composeIcon(ResolveInfo ri) {
        final int S = 208, X = 24, Y = 14, DEPTH = 12;
        final float R = S * 0.28f;
        Bitmap face = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas fc = new Canvas(face);
        try {
            Drawable d = ri.loadIcon(pm);
            if (d != null) {
                Path clip = new Path();
                clip.addRoundRect(new RectF(0, 0, S, S), R, R, Path.Direction.CW);
                fc.save();
                fc.clipPath(clip);
                if (android.os.Build.VERSION.SDK_INT >= 26 && d instanceof android.graphics.drawable.AdaptiveIconDrawable) {
                    android.graphics.drawable.AdaptiveIconDrawable ad = (android.graphics.drawable.AdaptiveIconDrawable) d;
                    int o = (int) (S * 0.25f);
                    Drawable bg = ad.getBackground(), fg = ad.getForeground();
                    if (bg != null) { bg.setBounds(-o, -o, S + o, S + o); bg.draw(fc); }
                    if (fg != null) { fg.setBounds(-o, -o, S + o, S + o); fg.draw(fc); }
                } else {
                    d.setBounds(0, 0, S, S);
                    d.draw(fc);
                }
                fc.restore();
            }
        } catch (Exception ignored) {}

        // puffy shading, only on the painted pixels
        Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
        sp.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
        sp.setShader(new RadialGradient(S * 0.3f, S * 0.22f, S * 0.9f, 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        fc.drawRect(0, 0, S, S, sp);
        sp.setShader(new LinearGradient(0, S * 0.5f, 0, S, 0x00000000, 0x55000000, Shader.TileMode.CLAMP));
        fc.drawRect(0, 0, S, S, sp);
        sp.setShader(new LinearGradient(0, 0, 0, S * 0.45f, 0x55FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        fc.drawOval(new RectF(S * 0.06f, -S * 0.12f, S * 0.94f, S * 0.46f), sp);
        Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(3);
        rim.setShader(new LinearGradient(0, 0, 0, S, 0x99FFFFFF, 0x66000000, Shader.TileMode.CLAMP));
        fc.drawRoundRect(new RectF(1.5f, 1.5f, S - 1.5f, S - 1.5f), R, R, rim);

        Bitmap out = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        // soft shadow under the slab
        Paint blur = new Paint();
        blur.setMaskFilter(new BlurMaskFilter(9, BlurMaskFilter.Blur.NORMAL));
        int[] off = new int[2];
        Bitmap sh = face.extractAlpha(blur, off);
        Paint shp = new Paint();
        shp.setColor(0xAA000000);
        c.drawBitmap(sh, X + off[0], Y + DEPTH + 10 + off[1], shp);

        // thickness: stacked darker copies below the face
        Paint dark = new Paint(Paint.FILTER_BITMAP_FLAG);
        ColorMatrix cm = new ColorMatrix();
        cm.setScale(0.42f, 0.42f, 0.42f, 1f);
        dark.setColorFilter(new ColorMatrixColorFilter(cm));
        for (int i = DEPTH; i >= 1; i--) c.drawBitmap(face, X, Y + i, dark);

        c.drawBitmap(face, X, Y, bp);
        return out;
    }

    /** The themed clay art, placed on the chosen icon style (3D keeps the art as it is). */
    private Bitmap styled(String name, Bitmap art) {
        if (art == null || iconStyle == IconStyler.CLAY) return art;
        String k = iconStyle + ":" + name;
        Bitmap b = styledCache.get(k);
        if (b == null) { b = IconStyler.tileFromArt(art, iconStyle); styledCache.put(k, b); }
        return b;
    }

    private Bitmap calendarBitmap() {
        int day = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
        Bitmap b = res("calendar_" + day);
        if (b != null) return styled("calendar_" + day, b);
        return styled("ic_calendar", res("ic_calendar"));
    }

    /** A still image of the icon (used inside folders and drag shadows). */
    private Bitmap stillBitmap(App a) {
        if (a.isCal()) return calendarBitmap();
        if (a.isClock()) {
            Bitmap c = res("ic_clock");
            return c != null ? styled("ic_clock", c) : styled("ic_clock_face", res("ic_clock_face"));
        }
        return a.plain;
    }

    private void buildDock() {
        dockApps.clear();
        String saved = prefs.getString("dock_apps", null);
        if (saved != null) {
            for (String k : saved.split("\n")) {
                App a = byKey.get(k);
                if (a != null && !dockApps.contains(a) && dockApps.size() < dockCount) dockApps.add(a);
            }
        }
        String[] order = {"ic_phone", "ic_messages", "ic_camera", "ic_chrome", "ic_gallery", "ic_settings", "ic_contacts"};
        for (String ic : order) {
            if (dockApps.size() >= dockCount) break;
            for (App a : apps) {
                if (ic.equals(a.iconName) && !dockApps.contains(a)) { dockApps.add(a); break; }
            }
        }
    }

    private void saveDock() {
        StringBuilder sb = new StringBuilder();
        for (App a : dockApps) { if (sb.length() > 0) sb.append('\n'); sb.append(a.key); }
        prefs.edit().putString("dock_apps", sb.toString()).apply();
        rebuildHome();
    }

    private void setDockSlot(int slot, App app) {
        List<App> l = new ArrayList<>(dockApps);
        int existing = l.indexOf(app);
        if (slot >= l.size()) { if (existing >= 0) l.remove(existing); l.add(app); }
        else if (existing >= 0) Collections.swap(l, existing, slot);
        else l.set(slot, app);
        dockApps.clear();
        dockApps.addAll(l);
        saveDock();
    }

    private void removeFromDock(App a) {
        if (dockApps.size() <= 3) { toast("The dock keeps at least 3 icons. Use Replace instead."); return; }
        dockApps.remove(a);
        dockCount = dockApps.size();
        prefs.edit().putInt("dock_count", dockCount).apply();
        saveDock();
        if (settings != null) settings.sync();
    }

    private void pickApp(String title, final AppPick cb) {
        final String[] arr = new String[apps.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = apps.get(i).label;
        new AlertDialog.Builder(this).setTitle(title).setItems(arr, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int w) { cb.on(apps.get(w)); }
        }).show();
    }

    /** Dock slots list: tap a slot, then pick the app for it. */
    private void editDock() {
        String[] arr = new String[dockCount];
        for (int i = 0; i < arr.length; i++)
            arr[i] = (i + 1) + ".  " + (i < dockApps.size() ? dockApps.get(i).label : "(empty)");
        new AlertDialog.Builder(this).setTitle("Dock apps: tap a slot").setItems(arr, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                final int slot = which;
                pickApp("Dock slot " + (slot + 1), new AppPick() {
                    public void on(App a) { setDockSlot(slot, a); editDock(); }
                });
            }
        }).setPositiveButton("Done", null).show();
    }

    private void addToDock(final App a) {
        if (dockApps.contains(a)) { toast(a.label + " is already in the dock"); return; }
        String[] arr = new String[dockCount];
        for (int i = 0; i < arr.length; i++)
            arr[i] = i < dockApps.size() ? "Replace " + dockApps.get(i).label : "Empty slot " + (i + 1);
        new AlertDialog.Builder(this).setTitle("Put " + a.label + " in the dock").setItems(arr, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) { setDockSlot(which, a); }
        }).show();
    }

    private void dockMenu(final App a) {
        final String[] arr = {"Replace...", "Remove from dock", "App info", "Uninstall"};
        new AlertDialog.Builder(this).setTitle(a.label).setItems(arr, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int w) {
                if (w == 0) {
                    final int slot = dockApps.indexOf(a);
                    pickApp("Replace " + a.label, new AppPick() { public void on(App n) { setDockSlot(slot, n); } });
                } else if (w == 1) removeFromDock(a);
                else if (w == 2) appInfo(a);
                else uninstall(a);
            }
        }).show();
    }

    private void applyDockStyle() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(((dockAlpha * 255 / 100) << 24) | 0x00FFFFFF);
        g.setCornerRadius(dp(dockRadius));
        dock.setBackground(g);
        dock.setVisibility(dockShow ? View.VISIBLE : View.GONE);
    }

    // ================================================================ home layout model

    private Item appItem(String key) {
        Item it = new Item();
        it.type = T_APP;
        it.key = key;
        return it;
    }

    private void loadItems() {
        items.clear();
        String json = prefs.getString("layout", null);
        if (json != null) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    int t = o.optInt("t");
                    if (t == T_APP) {
                        String k = o.optString("k");
                        if (byKey.containsKey(k)) items.add(appItem(k));
                    } else if (t == T_FOLDER) {
                        Item f = new Item();
                        f.type = T_FOLDER;
                        f.name = o.optString("n", "Folder");
                        JSONArray ka = o.optJSONArray("a");
                        if (ka != null) for (int j = 0; j < ka.length(); j++) {
                            String k = ka.optString(j);
                            if (byKey.containsKey(k) && !f.keys.contains(k)) f.keys.add(k);
                        }
                        if (f.keys.size() > 1) items.add(f);
                        else if (f.keys.size() == 1) items.add(appItem(f.keys.get(0)));
                    } else if (t == T_WIDGET) {
                        Item w = new Item();
                        w.type = T_WIDGET;
                        w.wid = o.optInt("id");
                        w.w = Math.max(1, Math.min(COLS, o.optInt("w", 1)));
                        w.h = Math.max(1, Math.min(ROWS, o.optInt("h", 1)));
                        if (wm.getAppWidgetInfo(w.wid) != null) items.add(w);
                        else host.deleteAppWidgetId(w.wid);
                    }
                }
            } catch (JSONException ignored) {}
        } else if (prefs.contains("pins")) {
            for (String k : prefs.getString("pins", "").split("\n"))
                if (k.length() > 0 && byKey.containsKey(k)) items.add(appItem(k));
        } else {
            List<App> themed = new ArrayList<>(), rest = new ArrayList<>();
            for (App a : apps) {
                if (dockApps.contains(a)) continue;
                if (a.iconName != null) themed.add(a); else rest.add(a);
            }
            for (App a : themed) items.add(appItem(a.key));
            for (App a : rest) items.add(appItem(a.key));
        }
        saveItems();
    }

    private void saveItems() {
        JSONArray arr = new JSONArray();
        for (Item it : items) {
            try {
                JSONObject o = new JSONObject();
                o.put("t", it.type);
                if (it.type == T_APP) {
                    o.put("k", it.key);
                } else if (it.type == T_FOLDER) {
                    o.put("n", it.name);
                    JSONArray ka = new JSONArray();
                    for (String k : it.keys) ka.put(k);
                    o.put("a", ka);
                } else {
                    o.put("id", it.wid);
                    o.put("w", it.w);
                    o.put("h", it.h);
                }
                arr.put(o);
            } catch (JSONException ignored) {}
        }
        prefs.edit().putString("layout", arr.toString()).apply();
    }

    private boolean onHome(String key) {
        for (Item it : items) {
            if (it.type == T_APP && key.equals(it.key)) return true;
            if (it.type == T_FOLDER && it.keys.contains(key)) return true;
        }
        return false;
    }

    private void pin(App a) {
        if (!onHome(a.key)) { items.add(appItem(a.key)); saveItems(); rebuildHome(); }
        Toast.makeText(this, a.label + " added to home", Toast.LENGTH_SHORT).show();
    }

    /** With the drawer off every app lives on the home screen. */
    private void addAllToHome() {
        boolean changed = false;
        for (App a : apps) if (!onHome(a.key) && !hiddenPkgs.contains(a.pkg)) { items.add(appItem(a.key)); changed = true; }
        if (changed) saveItems();
    }

    private boolean needsDrawerToRemove(Item it) {
        if (!drawerEnabled && it.type == T_APP) {
            Toast.makeText(this, "Turn on the app drawer to remove apps from home", Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    private void unpin(App a) {
        if (!drawerEnabled) { Toast.makeText(this, "Turn on the app drawer to remove apps from home", Toast.LENGTH_SHORT).show(); return; }
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (it.type == T_APP && a.key.equals(it.key)) { items.remove(i); break; }
        }
        saveItems();
        rebuildHome();
    }

    /** First-fit placement: items flow left to right, top to bottom; widgets take several cells. */
    private int place() {
        List<boolean[]> occ = new ArrayList<>();
        int cp = 0, cc = 0;
        for (Item it : items) {
            if (it.type == T_WIDGET) { it.w = Math.min(COLS, it.w); it.h = Math.min(ROWS, it.h); }
            int w = it.type == T_WIDGET ? it.w : 1, h = it.type == T_WIDGET ? it.h : 1;
            boolean done = false;
            int p = cp, c = cc;
            while (!done) {
                while (occ.size() <= p) occ.add(new boolean[COLS * ROWS]);
                boolean[] o = occ.get(p);
                for (; c < COLS * ROWS; c++) {
                    int r = c / COLS, cl = c % COLS;
                    if (cl + w > COLS || r + h > ROWS) continue;
                    boolean free = true;
                    for (int dr = 0; dr < h && free; dr++)
                        for (int dc = 0; dc < w; dc++)
                            if (o[(r + dr) * COLS + cl + dc]) { free = false; break; }
                    if (!free) continue;
                    for (int dr = 0; dr < h; dr++)
                        for (int dc = 0; dc < w; dc++) o[(r + dr) * COLS + cl + dc] = true;
                    it.page = p; it.row = r; it.col = cl;
                    cp = p; cc = c;
                    done = true;
                    break;
                }
                if (!done) { p++; c = 0; }
            }
        }
        return Math.max(1, occ.size());
    }

    // ================================================================ UI

    private static int clampi(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private void buildUi() {
        root = new FrameLayout(this);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, dp(12), 0, dp(8));

        pager = new PagerHSV(this);
        pager.setHorizontalScrollBarEnabled(false);
        pager.setOverScrollMode(View.OVER_SCROLL_NEVER);
        pagesBox = new LinearLayout(this);
        pagesBox.setOrientation(LinearLayout.HORIZONTAL);
        pager.addView(pagesBox, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        col.addView(pager, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        pager.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override public void onScrollChange(View v, int x, int y, int ox, int oy) { updateDots(); applyScrollFx(x); }
        });
        pager.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                if ((b - t) != (ob - ot) && (b - t) > 0) pager.post(rebuildRun);
            }
        });

        dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER);
        col.addView(dots, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(18)));

        dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        applyDockStyle();
        dock.setPadding(dp(6), dp(8), dp(6), dp(8));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.setMargins(dp(12), dp(4), dp(12), dp(8));
        col.addView(dock, dlp);

        root.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- drawer
        drawer = new FrameLayout(this);
        drawer.setBackgroundColor(Theme.bg);
        drawer.setVisibility(View.GONE);
        LinearLayout dcol = new LinearLayout(this);
        dcol.setOrientation(LinearLayout.VERTICAL);
        dcol.setPadding(dp(8), dp(16), dp(8), 0);

        search = new EditText(this);
        search.setHint("Search apps");
        search.setHintTextColor(Theme.a(0x88));
        search.setTextColor(Theme.text);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_GO);
        search.setPadding(dp(18), dp(10), dp(18), dp(10));
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(Theme.a(0x26));
        sbg.setCornerRadius(dp(26));
        search.setBackground(sbg);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.setMargins(dp(8), 0, dp(8), dp(8));
        dcol.addView(search, slp);

        grid = new GridView(this);
        grid.setNumColumns(drawerCols);
        grid.setVerticalSpacing(dp(10));
        grid.setSelector(android.R.color.transparent);
        grid.setVerticalScrollBarEnabled(false);
        adapter = new DrawerAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override public void onItemClick(android.widget.AdapterView<?> p, View v, int pos, long id) { launch(shown.get(pos), v); }
        });
        grid.setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(android.widget.AdapterView<?> p, View v, int pos, long id) {
                appMenu(shown.get(pos), true, false); return true;
            }
        });
        searchExtras = new LinearLayout(this);
        searchExtras.setOrientation(LinearLayout.VERTICAL);
        searchExtras.setVisibility(View.GONE);
        LinearLayout.LayoutParams xlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        xlp.setMargins(dp(8), 0, dp(8), dp(8));
        dcol.addView(searchExtras, xlp);

        drawerPager = new PagerHSV(this);
        drawerPager.setHorizontalScrollBarEnabled(false);
        drawerPager.setOverScrollMode(View.OVER_SCROLL_NEVER);
        drawerPagesBox = new LinearLayout(this);
        drawerPagesBox.setOrientation(LinearLayout.HORIZONTAL);
        drawerPager.addView(drawerPagesBox, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout body = new FrameLayout(this);
        body.addView(grid, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        body.addView(drawerPager, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dcol.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        applyDrawerConfig();
        drawer.addView(dcol, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(drawer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { filter(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
        search.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent e) {
                if (!shown.isEmpty()) launch(shown.get(0), null);
                else if (firstExtra != null) firstExtra.run();
                return true;
            }
        });

        // ---- drag target bar (visible only while dragging)
        targetBar = new LinearLayout(this);
        targetBar.setOrientation(LinearLayout.HORIZONTAL);
        targetBar.setBackgroundColor(0xDD000000);
        targetBar.setVisibility(View.GONE);
        tRemove = targetLabel("Remove");
        tInfo = targetLabel("App info");
        tUninstall = targetLabel("Uninstall");
        targetBar.addView(tRemove, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        targetBar.addView(tInfo, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        targetBar.addView(tUninstall, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        root.addView(targetBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64), Gravity.TOP));

        settings = new SettingsPanel(this, new SettingsPanel.Host() {
            @Override public int iconStyle() { return iconStyle; }
            @Override public int iconSize() { return iconSizePct; }
            @Override public int blur() { return prefs.getInt("wp_blur", 0); }
            @Override public void setIconStyle(int s) { changeIconStyle(s); }
            @Override public void setIconSize(int p) { changeIconSize(p); }
            @Override public void setBlur(int p) { changeBlur(p); }
            @Override public Bitmap sample(int s) { return sampleIcon(s); }
            @Override public Bitmap defaultThumb(int i) { return WallpaperHelper.decodeRes(getResources(), DEFAULT_WALLPAPERS[i], 300); }
            @Override public void useDefault(int i) { MainActivity.this.useDefault(i); }
            @Override public void pickGallery() { MainActivity.this.pickGallery(); }
            @Override public void openLive() { MainActivity.this.openLive(); }
            @Override public void openCrop() { MainActivity.this.openCropFromSaved(); }
            @Override public void close() { MainActivity.this.close(); }
            @Override public void addWidget() { MainActivity.this.close(); pickWidget(); }
            @Override public void chooseDock() { editDock(); }
            @Override public String gestureLabel(String k, int d) { return MainActivity.this.gestureLabel(k, d); }
            @Override public void chooseGesture(String k, int d, Runnable done) { MainActivity.this.chooseGesture(k, d, done); }
            @Override public boolean badgeAccess() { return MainActivity.this.badgeAccess(); }
            @Override public void openBadgeAccess() {
                try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
                catch (Exception e) { toast("Not available on this phone"); }
            }
            @Override public void chooseHidden() {
                chooseApps("Hide from the app drawer", hiddenPkgs, "hidden_pkgs", new Runnable() { public void run() { applyHidden(); } });
            }
            @Override public void chooseProtected() { chooseApps("Protect with the number lock", protectedPkgs, "protected_pkgs", null); }
            @Override public void backup() { backupSettings(); }
            @Override public void restore() { restoreSettings(); }
            @Override public void resetAll() { MainActivity.this.resetAll(); }
            @Override public void lockNow() { MainActivity.this.close(); showMainLock(); }
            @Override public void lockPreview() { showLock("Preview: tap smallest to biggest", lockCount, true, null); }
            @Override public int geti(String k, int d) { return prefs.getInt(k, d); }
            @Override public boolean getb(String k, boolean d) { return prefs.getBoolean(k, d); }
            @Override public void puti(String k, int v) { prefs.edit().putInt(k, v).apply(); onSetting(k); }
            @Override public void putb(String k, boolean v) { prefs.edit().putBoolean(k, v).apply(); onSetting(k); }
        });
        settings.setVisibility(View.GONE);
        root.addView(settings, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
    }

    private TextView targetLabel(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private int scaled(float dpValue) { return dp(dpValue * iconSizePct / 100f); }

    private View makeIcon(App a, int size) {
        View v;
        if (a.isClock()) {
            ClockView cv = new ClockView(this, res("ic_clock_face"), res("ic_clock_hour"), res("ic_clock_min"), res("ic_clock_sec"));
            if (iconStyle == IconStyler.CLAY) {
                v = cv;
            } else {
                FrameLayout fl = new FrameLayout(this);
                ImageView tile = new ImageView(this);
                tile.setScaleType(ImageView.ScaleType.FIT_CENTER);
                String tk = iconStyle + ":tile_empty";
                Bitmap tb = styledCache.get(tk);
                if (tb == null) { tb = IconStyler.tileFromArt(null, iconStyle); styledCache.put(tk, tb); }
                tile.setImageBitmap(tb);
                fl.addView(tile, new FrameLayout.LayoutParams(-1, -1));
                int in = (int) (size * 0.62f);
                FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(in, in, Gravity.CENTER);
                fl.addView(cv, clp);
                v = fl;
            }
        } else {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setImageBitmap(a.isCal() ? calendarBitmap() : a.plain);
            if (fxShadows) {
                final int rr = (int) (size * 0.22f);
                iv.setOutlineProvider(new ViewOutlineProvider() {
                    @Override public void getOutline(View view, Outline o) {
                        int in = (int) (view.getWidth() * 0.08f);
                        o.setRoundRect(in, in, view.getWidth() - in, view.getHeight() - in, rr);
                        o.setAlpha(0.55f);
                    }
                });
                iv.setElevation(dp(2 + fxDepth * 0.10f));
            }
            v = iv;
        }
        v.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return v;
    }

    private View makeCellWith(View icon, String label) { return makeCellWith(icon, label, Color.WHITE); }

    private View makeCellWith(View icon, String label, int labelColor) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(2), dp(6), dp(2), dp(6));
        c.addView(icon);
        if (label != null) {
            TextView t = new TextView(this);
            t.setText(label);
            t.setTextColor(labelColor);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, labelSp);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setGravity(Gravity.CENTER);
            if (labelColor == Color.WHITE) t.setShadowLayer(4, 0, 1, 0xCC000000);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setPadding(0, dp(4), 0, 0);
            c.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return c;
    }

    private View makeCell(App a, boolean label) {
        boolean lab = label && showLabels;
        View icon = makeIcon(a, scaled(lab ? 58 : 62));
        return makeCellWith(badged(icon, pkgsOf(a), true), lab ? a.label : null);
    }

    /** Dock cell: shrinks the icon so 3 to 6 of them always fit. */
    private View makeDockCell(App a) {
        int per = (screenW - dp(36)) / Math.max(1, dockApps.size());
        int size = Math.max(dp(36), Math.min(scaled(62), per - dp(6)));
        View icon = makeIcon(a, size);
        if (badgeDock) icon = badged(icon, pkgsOf(a), true);
        return makeCellWith(icon, null);
    }

    /** Cell that always shows its name (app drawer, folder window). */
    private View makeNamedCell(App a) { return makeNamedCellC(a, Theme.text); }

    private View makeNamedCellC(App a, int color) {
        return makeCellWith(badged(makeIcon(a, scaled(58)), pkgsOf(a), false), a.label, color);
    }

    // ---- notification badges

    private List<String> pkgsOf(App a) {
        List<String> l = new ArrayList<>(1);
        l.add(a.pkg);
        return l;
    }

    private int badgeCount(List<String> pkgs) {
        Map<String, Integer> m = BadgeService.counts;
        int n = 0;
        for (String p : pkgs) { Integer c = m.get(p); if (c != null) n += c; }
        return n;
    }

    private void updateBadge(TextView b, List<String> pkgs) {
        int n = badgeCount(pkgs);
        if (n <= 0) { b.setVisibility(View.GONE); return; }
        b.setVisibility(View.VISIBLE);
        b.setText(badgeStyle == 1 ? (n > 99 ? "99+" : String.valueOf(n)) : "");
    }

    /** Wraps an icon so a red dot or number can sit on its top-right corner. Style 2 = hidden. */
    private View badged(View icon, List<String> pkgs, boolean track) {
        if (badgeStyle == 2) return icon;
        ViewGroup.LayoutParams lp = icon.getLayoutParams();
        FrameLayout f = new FrameLayout(this);
        f.setClipChildren(false);
        f.setLayoutParams(lp);
        icon.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        f.addView(icon);
        TextView b = new TextView(this);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        b.setIncludeFontPadding(false);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFFFF3B30);
        g.setCornerRadius(dp(10));
        b.setBackground(g);
        FrameLayout.LayoutParams bl;
        if (badgeStyle == 1) {
            b.setMinWidth(dp(18));
            b.setPadding(dp(4), 0, dp(4), 0);
            bl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(18), Gravity.TOP | Gravity.END);
        } else {
            bl = new FrameLayout.LayoutParams(dp(11), dp(11), Gravity.TOP | Gravity.END);
            bl.topMargin = dp(2);
            bl.rightMargin = dp(2);
        }
        f.addView(b, bl);
        updateBadge(b, pkgs);
        if (track) { BadgeRef r = new BadgeRef(); r.v = b; r.pkgs = pkgs; badgeRefs.add(r); }
        return f;
    }

    private void refreshBadges() {
        for (BadgeRef r : badgeRefs) updateBadge(r.v, r.pkgs);
    }

    private boolean badgeAccess() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(getPackageName());
    }

    private void requestBadgeRebind() {
        if (!badgeAccess() || BadgeService.connected) return;
        try {
            android.service.notification.NotificationListenerService.requestRebind(new ComponentName(this, BadgeService.class));
        } catch (Exception ignored) {}
    }

    private Bitmap folderBitmap(Item f) {
        Bitmap b = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        RectF r = new RectF(14, 14, 242, 242);
        if (folderStyle == 1) {            // transparent: only a faint hint of a tile
            p.setColor(0x14FFFFFF);
            c.drawRoundRect(r, 62, 62, p);
        } else if (folderStyle == 2) {     // 3D slab
            p.setColor(0xFF2E3260);
            c.drawRoundRect(new RectF(14, 30, 242, 250), 62, 62, p);
            p.setShader(new LinearGradient(0, 14, 0, 232, 0xFFA3AAF0, 0xFF5C63B8, Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(14, 14, 242, 232), 62, 62, p);
            p.setShader(null);
            p.setColor(0x33FFFFFF);
            c.drawRoundRect(new RectF(28, 22, 228, 104), 52, 52, p);
        } else if (folderStyle == 3) {     // rounded: a full circle
            p.setColor(0x77FFFFFF);
            c.drawOval(new RectF(10, 10, 246, 246), p);
        } else {                           // glass
            p.setColor(0x55FFFFFF);
            c.drawRoundRect(r, 62, 62, p);
            p.setShader(new LinearGradient(0, 14, 0, 242, 0x66FFFFFF, 0x08FFFFFF, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, 62, 62, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(4);
            p.setColor(0x99FFFFFF);
            c.drawRoundRect(r, 62, 62, p);
            p.setStyle(Paint.Style.FILL);
        }
        p.setColor(Color.WHITE);
        int n = Math.min(4, f.keys.size());
        boolean circle = folderStyle == 3;
        int sz = circle ? 80 : 88, gx = circle ? 44 : 34, gy = circle ? 44 : 34, step = circle ? 88 : 100;
        if (folderStyle == 2) { gy = 30; }
        for (int i = 0; i < n; i++) {
            App a = byKey.get(f.keys.get(i));
            if (a == null) continue;
            Bitmap ic = stillBitmap(a);
            if (ic == null) continue;
            int x = gx + (i % 2) * step, y = gy + (i / 2) * step;
            c.drawBitmap(ic, null, new RectF(x, y, x + sz, y + sz), p);
        }
        return b;
    }

    private View makeFolderCell(Item f) {
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(folderBitmap(f));
        int fsz = scaled(58) * folderSize / 100;
        iv.setLayoutParams(new LinearLayout.LayoutParams(fsz, fsz));
        View icon = iv;
        if (badgeFolders) {
            List<String> pk = new ArrayList<>();
            for (String k : f.keys) { App fa = byKey.get(k); if (fa != null && !pk.contains(fa.pkg)) pk.add(fa.pkg); }
            icon = badged(iv, pk, true);
        }
        return makeCellWith(icon, showLabels ? f.name : null);
    }

    private float pagePad() { return dp(6); }
    private float cellW() { return (screenW - 2 * pagePad()) / COLS; }
    private float cellH() { return pager.getHeight() / (float) ROWS; }

    private void rebuildHome() {
        if (pagesBox == null || pager.getHeight() <= 0 || dragItem != null) return;
        int curPage = pager.page();
        pagesBox.removeAllViews();
        widgetViews.clear();
        badgeRefs.clear();
        floatViews.clear();
        int pageCount = place();
        float cw = cellW(), ch = cellH(), pad = pagePad();

        List<FrameLayout> pages = new ArrayList<>();
        for (int p = 0; p < pageCount; p++) {
            FrameLayout page = new FrameLayout(this);
            page.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) { homeMenu(); return true; }
            });
            pagesBox.addView(page, new LinearLayout.LayoutParams(screenW, ViewGroup.LayoutParams.MATCH_PARENT));
            pages.add(page);
        }

        for (final Item it : items) {
            FrameLayout page = pages.get(it.page);
            int left = (int) (pad + it.col * cw), top = (int) (it.row * ch);
            if (it.type == T_WIDGET) {
                AppWidgetProviderInfo info = wm.getAppWidgetInfo(it.wid);
                if (info == null) continue;
                int gap = dp(widgetGap);
                int wpx = (int) (it.w * cw) - 2 * gap, hpx = (int) (it.h * ch) - 2 * gap;
                AppWidgetHostView hv;
                try { hv = host.createView(this, it.wid, info); } catch (Exception e) { continue; }
                float dens = getResources().getDisplayMetrics().density;
                int wdp = (int) (wpx / dens), hdp = (int) (hpx / dens);
                try { hv.updateAppWidgetSize(null, wdp, hdp, wdp, hdp); } catch (Exception ignored) {}
                hv.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override public boolean onLongClick(View v) {
                        if (widgetsLocked) { toast("Widgets are locked (Voltage settings > Widgets)"); return true; }
                        startDrag(it, v);
                        return true;
                    }
                });
                if (widgetRadius > 0) {
                    final int rr = dp(widgetRadius);
                    hv.setOutlineProvider(new ViewOutlineProvider() {
                        @Override public void getOutline(View view, Outline o) { o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), rr); }
                    });
                    hv.setClipToOutline(true);
                }
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(wpx, hpx);
                lp.leftMargin = left + gap;
                lp.topMargin = top + gap;
                page.addView(hv, lp);
                widgetViews.add(hv);

                if (!widgetsMenuBtn || widgetsLocked) continue;
                TextView menu = new TextView(this);
                menu.setText("\u22EE");
                menu.setTextColor(Color.WHITE);
                menu.setGravity(Gravity.CENTER);
                menu.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                GradientDrawable mg = new GradientDrawable();
                mg.setShape(GradientDrawable.OVAL);
                mg.setColor(0x88000000);
                menu.setBackground(mg);
                menu.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { widgetMenu(it); }
                });
                FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(dp(28), dp(28));
                mlp.leftMargin = left + gap + wpx - dp(32);
                mlp.topMargin = top + gap + dp(4);
                page.addView(menu, mlp);
                continue;
            }

            View cell;
            if (it.type == T_APP) {
                final App a = byKey.get(it.key);
                if (a == null) continue;
                cell = makeCell(a, true);
                cell.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { launch(a, v); }
                });
            } else {
                cell = makeFolderCell(it);
                cell.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { showFolder(it); }
                });
            }
            cell.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) { startDrag(it, v); return true; }
            });
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) cw, (int) ch);
            lp.leftMargin = left;
            lp.topMargin = top;
            page.addView(cell, lp);
            floatViews.add(cell);
        }

        pager.setPages(pageCount, screenW);
        try { if (wpm != null) wpm.setWallpaperOffsetSteps(pageCount > 1 ? 1f / (pageCount - 1) : 1f, 1f); } catch (Exception ignored) {}
        final int target = Math.min(curPage, pageCount - 1);
        pager.post(new Runnable() { public void run() { pager.goToNow(target); updateDots(); applyScrollFx(pager.getScrollX()); } });

        dock.removeAllViews();
        for (final App a : dockApps) {
            View cell = makeDockCell(a);
            cell.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { launch(a, v); } });
            cell.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) { dockMenu(a); return true; }
            });
            dock.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        updateDots();
    }

    private void updateDots() {
        if (dots == null) return;
        int n = pager.count(), cur = pager.page();
        dots.removeAllViews();
        if (n <= 1) return;
        for (int i = 0; i < n; i++) {
            View d = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(i == cur ? 0xFFFFFFFF : 0x66FFFFFF);
            d.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(7), dp(7));
            lp.setMargins(dp(4), 0, dp(4), 0);
            dots.addView(d, lp);
        }
    }

    private static boolean hit(View v, float x, float y) {
        int[] l = new int[2];
        v.getLocationOnScreen(l);
        return x >= l[0] && x <= l[0] + v.getWidth() && y >= l[1] && y <= l[1] + v.getHeight();
    }

    private boolean startsInWidget(MotionEvent e) {
        for (View v : widgetViews) if (hit(v, e.getRawX(), e.getRawY())) return true;
        return false;
    }

    // ================================================================ actions

    private void launch(final App a, final View from) {
        if (appLock && protectedPkgs.contains(a.pkg) && !unlockedRecently(a)) {
            showLock("Unlock " + a.label, Math.max(4, lockCount - 1), true, new Runnable() {
                public void run() { appUnlocked.put(a.pkg, SystemClock.uptimeMillis()); doLaunch(a, null); }
            });
            return;
        }
        doLaunch(a, from);
    }

    private void doLaunch(App a, View from) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(a.cn).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            Bundle o = null;
            o = openOptions(from);
            startActivity(i, o);
        } catch (Exception e) {
            Toast.makeText(this, "Can't open " + a.label, Toast.LENGTH_SHORT).show();
            return;
        }
        if (drawerOpen) {
            root.postDelayed(new Runnable() { public void run() { hideDrawerNow(); } }, 350);
        }
    }

    private void appInfo(App a) {
        try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg))); }
        catch (Exception e) { Toast.makeText(this, "Not available", Toast.LENGTH_SHORT).show(); }
    }

    private void uninstall(App a) {
        try { startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg))); }
        catch (Exception e) { Toast.makeText(this, "Can't uninstall this app", Toast.LENGTH_SHORT).show(); }
    }

    private void appMenu(final App a, boolean fromDrawer, boolean fromHome) {
        List<String> list = new ArrayList<>();
        if (fromDrawer && !onHome(a.key)) list.add("Add to home screen");
        if (fromHome) list.add("Remove from home");
        if (fromDrawer) list.add("Hide app");
        if ((fromDrawer || fromHome) && !dockApps.contains(a)) list.add("Add to dock");
        list.add("App info");
        list.add("Uninstall");
        final String[] arr = list.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle(a.label).setItems(arr, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int w) {
                String s = arr[w];
                if (s.equals("Add to dock")) addToDock(a);
                else if (s.equals("Add to home screen")) pin(a);
                else if (s.equals("Hide app")) hideApp(a);
                else if (s.startsWith("Remove")) unpin(a);
                else if (s.equals("App info")) appInfo(a);
                else uninstall(a);
            }
        }).show();
    }

    private void homeMenu() {
        final String[] arr = {"Voltage settings", "Add widget", drawerEnabled ? "Open all apps" : "Search", "Phone settings"};
        new AlertDialog.Builder(this).setItems(arr, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int w) {
                try {
                    if (w == 0) openSettings();
                    else if (w == 1) pickWidget();
                    else if (w == 2) openDrawer();
                    else startActivity(new Intent(Settings.ACTION_SETTINGS));
                } catch (Exception e) { Toast.makeText(MainActivity.this, "Not available", Toast.LENGTH_SHORT).show(); }
            }
        }).show();
    }

    private void expandNotifications() {
        try {
            Object sb = getSystemService("statusbar");
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb);
        } catch (Throwable t) {
            try {
                Object sb = getSystemService("statusbar");
                Class.forName("android.app.StatusBarManager").getMethod("expand").invoke(sb);
            } catch (Throwable t2) {
                Toast.makeText(this, "Swipe down from the very top edge for notifications", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ================================================================ settings

    private boolean settingsOpen() { return settings != null && settings.getVisibility() == View.VISIBLE; }

    private void openSettings() {
        if (drawerOpen) hideDrawerNow();
        settings.scrollToTop();
        settings.refreshStyles();
        settings.refreshBadgeStatus();
        settings.setAlpha(0f);
        settings.setVisibility(View.VISIBLE);
        settings.animate().alpha(1f).setDuration(dur(180)).start();
    }

    private void close() {
        if (!settingsOpen()) return;
        settings.animate().alpha(0f).setDuration(dur(150)).withEndAction(new Runnable() {
            public void run() { settings.setVisibility(View.GONE); }
        }).start();
    }

    private void changeIconStyle(int s) {
        if (s == iconStyle) return;
        iconStyle = s;
        prefs.edit().putInt("icon_style", s).apply();
        reloadAll();
    }

    private void changeIconSize(int pct) {
        iconSizePct = pct;
        prefs.edit().putInt("icon_size", pct).apply();
        rebuildHome();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private Bitmap sampleIcon(int style) {
        Bitmap art = res("ic_phone");
        if (style == IconStyler.CLAY) return art;
        return IconStyler.tileFromArt(art, style);
    }

    // ---- wallpaper

    private Point realSize() {
        Point p = new Point();
        getWindowManager().getDefaultDisplay().getRealSize(p);
        return p;
    }

    private File wpOrig() { return new File(getFilesDir(), "wp_orig.jpg"); }
    private File wpBase() { return new File(getFilesDir(), "wp_base.jpg"); }

    private void toast(final String msg) {
        runOnUiThread(new Runnable() { public void run() { Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show(); } });
    }

    /** One of the wallpapers that ship with Voltage. */
    private void useDefault(final int index) {
        final Point sz = realSize();
        new Thread(new Runnable() {
            public void run() {
                Bitmap orig = WallpaperHelper.decodeRes(getResources(), DEFAULT_WALLPAPERS[index], 2400);
                if (orig == null) return;
                WallpaperHelper.saveJpeg(orig, wpOrig());
                commitWallpaper(WallpaperHelper.centerCrop(orig, sz.x, sz.y));
            }
        }).start();
    }

    /** Saves the cropped picture, blurs it by the saved amount and sets it as the system wallpaper. */
    private void commitWallpaper(final Bitmap cropped) {
        new Thread(new Runnable() {
            public void run() {
                WallpaperHelper.saveJpeg(cropped, wpBase());
                Bitmap out = WallpaperHelper.blur(cropped, prefs.getInt("wp_blur", 0));
                toast(WallpaperHelper.apply(MainActivity.this, out) ? "Wallpaper set" : "Couldn't set wallpaper");
            }
        }).start();
    }

    private void changeBlur(final int pct) {
        prefs.edit().putInt("wp_blur", pct).apply();
        new Thread(new Runnable() {
            public void run() {
                Bitmap base = WallpaperHelper.decodeFile(wpBase(), 4000);
                if (base == null) { toast("Choose a wallpaper from Voltage first"); return; }
                Bitmap out = WallpaperHelper.blur(base, pct);
                if (!WallpaperHelper.apply(MainActivity.this, out)) toast("Couldn't set wallpaper");
            }
        }).start();
    }

    private void pickGallery() {
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            startActivityForResult(Intent.createChooser(i, "Choose wallpaper"), REQ_PICK_WP);
        } catch (Exception e) { toast("No gallery app found"); }
    }

    private void importWallpaper(final Uri uri) {
        new Thread(new Runnable() {
            public void run() {
                final Bitmap b = WallpaperHelper.decodeUri(MainActivity.this, uri, 2400);
                if (b == null) { toast("Couldn't open that picture"); return; }
                WallpaperHelper.saveJpeg(b, wpOrig());
                runOnUiThread(new Runnable() { public void run() { showCrop(b); } });
            }
        }).start();
    }

    private void openCropFromSaved() {
        Bitmap b = WallpaperHelper.decodeFile(wpOrig(), 2400);
        if (b == null) { toast("Choose a wallpaper first"); return; }
        showCrop(b);
    }

    private void openLive() {
        try {
            startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
        } catch (Exception e) {
            try { startActivity(Intent.createChooser(new Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper")); }
            catch (Exception e2) { toast("Not available on this phone"); }
        }
    }

    /** Full-screen drag and pinch crop. */
    private void showCrop(Bitmap orig) {
        Point sz = realSize();
        final CropView cv = new CropView(this, orig, sz.x, sz.y);
        final Dialog d = new Dialog(this, android.R.style.Theme_Material_NoActionBar_Fullscreen);
        FrameLayout f = new FrameLayout(this);
        f.setBackgroundColor(0xFF000000);
        f.addView(cv, new FrameLayout.LayoutParams(-1, -1));

        TextView hint = new TextView(this);
        hint.setText("Drag and pinch to position");
        hint.setTextColor(0xFFCCCCCC);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        hint.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        hlp.bottomMargin = dp(96);
        f.addView(hint, hlp);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(20), 0, dp(20), dp(28));
        TextView cancel = cropButton("Cancel", 0x33FFFFFF);
        TextView ok = cropButton("Apply", 0xFFFF5A4F);
        LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0, dp(48), 1f);
        b1.rightMargin = dp(10);
        LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(0, dp(48), 1f);
        bar.addView(cancel, b1);
        bar.addView(ok, b2);
        f.addView(bar, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        cancel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); } });
        ok.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Bitmap out = cv.result();
                d.dismiss();
                commitWallpaper(out);
            }
        });
        d.setContentView(f);
        d.show();
    }

    private TextView cropButton(String label, int color) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(24));
        t.setBackground(g);
        return t;
    }

    /** Applies a changed generic setting (stored in prefs under its key). */
    private void onSetting(String k) {
        switch (k) {
            case "labels_show": showLabels = prefs.getBoolean(k, true); refreshAllCells(); break;
            case "labels_size": labelSp = clampi(prefs.getInt(k, 11), 9, 15); refreshAllCells(); break;
            case "grid_cols": COLS = clampi(prefs.getInt(k, 4), 3, 6); rebuildHome(); break;
            case "grid_rows": ROWS = clampi(prefs.getInt(k, 5), 4, 7); rebuildHome(); break;
            case "drawer_on":
                drawerEnabled = prefs.getBoolean(k, true);
                if (!drawerEnabled) { addAllToHome(); rebuildHome(); }
                break;
            case "drawer_cols": drawerCols = clampi(prefs.getInt(k, 4), 3, 6); applyDrawerConfig(); filter(search.getText().toString()); break;
            case "drawer_scroll": drawerScroll = clampi(prefs.getInt(k, 0), 0, 2); applyDrawerConfig(); filter(search.getText().toString()); break;
            case "widgets_menu": widgetsMenuBtn = prefs.getBoolean(k, true); rebuildHome(); break;
            case "widgets_lock": widgetsLocked = prefs.getBoolean(k, false); rebuildHome(); break;
            case "widgets_radius": widgetRadius = clampi(prefs.getInt(k, 0), 0, 32); rebuildHome(); break;
            case "widgets_gap": widgetGap = clampi(prefs.getInt(k, 4), 0, 12); rebuildHome(); break;
            case "dock_show": dockShow = prefs.getBoolean(k, true); applyDockStyle(); break;
            case "dock_count": dockCount = clampi(prefs.getInt(k, 4), 3, 6); buildDock(); rebuildHome(); break;
            case "dock_alpha": dockAlpha = clampi(prefs.getInt(k, 27), 0, 100); applyDockStyle(); break;
            case "dock_radius": dockRadius = clampi(prefs.getInt(k, 30), 0, 40); applyDockStyle(); break;
            case "gest_sens": swipeSens = clampi(prefs.getInt(k, 3), 1, 5); break;
            case "badge_style": badgeStyle = clampi(prefs.getInt(k, 0), 0, 2); refreshAllCells(); break;
            case "badge_dock": badgeDock = prefs.getBoolean(k, true); refreshAllCells(); break;
            case "badge_folders": badgeFolders = prefs.getBoolean(k, true); refreshAllCells(); break;
            case "anim_open": animOpen = clampi(prefs.getInt(k, 0), 0, 3); break;
            case "anim_close": animClose = clampi(prefs.getInt(k, 0), 0, 3); break;
            case "anim_scroll": animScroll = clampi(prefs.getInt(k, 0), 0, 3); applyScrollFx(pager.getScrollX()); break;
            case "anim_folder": animFolder = clampi(prefs.getInt(k, 0), 0, 3); break;
            case "anim_speed": animSpeed = clampi(prefs.getInt(k, 100), 50, 200); break;
            case "folder_style": folderStyle = clampi(prefs.getInt(k, 0), 0, 3); rebuildHome(); break;
            case "folder_size": folderSize = clampi(prefs.getInt(k, 100), 70, 130); rebuildHome(); break;
            case "theme_mode": case "theme_accent": case "theme_bg": applyThemeNow(); break;
            case "fx_shadows": fxShadows = prefs.getBoolean(k, false); refreshAllCells(); break;
            case "fx_depth": fxDepth = clampi(prefs.getInt(k, 50), 0, 100); refreshAllCells(); break;
            case "fx_parallax": fxParallax = prefs.getBoolean(k, false); if (resumed) startFx(); break;
            case "fx_float": fxFloat = prefs.getBoolean(k, false); if (resumed) startFx(); break;
            case "fx_wpscroll":
                fxWpScroll = prefs.getBoolean(k, false);
                if (!fxWpScroll) { try { wpm.setWallpaperOffsets(root.getWindowToken(), 0.5f, 0.5f); } catch (Exception ignored) {} }
                else applyScrollFx(pager.getScrollX());
                break;
            case "lock_on": lockOn = prefs.getBoolean(k, false); break;
            case "lock_screenoff": lockScreenOff = prefs.getBoolean(k, true); break;
            case "lock_count": lockCount = clampi(prefs.getInt(k, 6), 4, 9); break;
            case "lock_speed": lockSpeed = clampi(prefs.getInt(k, 3), 1, 5); break;
            case "hide_search": hideSearch = prefs.getBoolean(k, true); filter(search.getText().toString()); break;
            case "applock_on": appLock = prefs.getBoolean(k, false); break;
            case "applock_delay": applockDelay = clampi(prefs.getInt(k, 0), 0, 2); break;
            case "perf_mode": perfMode = clampi(prefs.getInt(k, 0), 0, 2); recheckSaver(); break;
            case "reduce_anim": reduceAnim = prefs.getBoolean(k, false); recheckSaver(); break;
            case "auto_saver": autoSaver = prefs.getBoolean(k, true); recheckSaver(); break;
            case "trim_mem": trimMem = prefs.getBoolean(k, true); break;
            case "search_settings": searchSettings = prefs.getBoolean(k, true); break;
            case "search_contacts":
                searchContacts = prefs.getBoolean(k, false);
                if (searchContacts && !hasContactsPermission()) requestPermissions(new String[]{android.Manifest.permission.READ_CONTACTS}, REQ_CONTACTS);
                break;
            default: break;
        }
    }

    private void loadExtraPrefs() {
        widgetsMenuBtn = prefs.getBoolean("widgets_menu", true);
        widgetsLocked = prefs.getBoolean("widgets_lock", false);
        widgetRadius = clampi(prefs.getInt("widgets_radius", 0), 0, 32);
        widgetGap = clampi(prefs.getInt("widgets_gap", 4), 0, 12);
        dockShow = prefs.getBoolean("dock_show", true);
        dockCount = clampi(prefs.getInt("dock_count", 4), 3, 6);
        dockAlpha = clampi(prefs.getInt("dock_alpha", 27), 0, 100);
        dockRadius = clampi(prefs.getInt("dock_radius", 30), 0, 40);
        swipeSens = clampi(prefs.getInt("gest_sens", 3), 1, 5);
        badgeStyle = clampi(prefs.getInt("badge_style", 0), 0, 2);
        badgeDock = prefs.getBoolean("badge_dock", true);
        badgeFolders = prefs.getBoolean("badge_folders", true);
        animOpen = clampi(prefs.getInt("anim_open", 0), 0, 3);
        animClose = clampi(prefs.getInt("anim_close", 0), 0, 3);
        animScroll = clampi(prefs.getInt("anim_scroll", 0), 0, 3);
        animFolder = clampi(prefs.getInt("anim_folder", 0), 0, 3);
        animSpeed = clampi(prefs.getInt("anim_speed", 100), 50, 200);
        folderStyle = clampi(prefs.getInt("folder_style", 0), 0, 3);
        folderSize = clampi(prefs.getInt("folder_size", 100), 70, 130);
        themeMode = clampi(prefs.getInt("theme_mode", 0), 0, 3);
        fxShadows = prefs.getBoolean("fx_shadows", false);
        fxDepth = clampi(prefs.getInt("fx_depth", 50), 0, 100);
        fxParallax = prefs.getBoolean("fx_parallax", false);
        fxWpScroll = prefs.getBoolean("fx_wpscroll", false);
        fxFloat = prefs.getBoolean("fx_float", false);
        lockOn = prefs.getBoolean("lock_on", false);
        lockScreenOff = prefs.getBoolean("lock_screenoff", true);
        lockCount = clampi(prefs.getInt("lock_count", 6), 4, 9);
        lockSpeed = clampi(prefs.getInt("lock_speed", 3), 1, 5);
        hideSearch = prefs.getBoolean("hide_search", true);
        appLock = prefs.getBoolean("applock_on", false);
        applockDelay = clampi(prefs.getInt("applock_delay", 0), 0, 2);
        perfMode = clampi(prefs.getInt("perf_mode", 0), 0, 2);
        reduceAnim = prefs.getBoolean("reduce_anim", false);
        autoSaver = prefs.getBoolean("auto_saver", true);
        trimMem = prefs.getBoolean("trim_mem", true);
        loadPkgSet(hiddenPkgs, "hidden_pkgs");
        loadPkgSet(protectedPkgs, "protected_pkgs");
        Theme.apply(themeMode, isNight(), prefs.getInt("theme_accent", Theme.ACCENTS[0]), prefs.getInt("theme_bg", Theme.BACKGROUNDS[1]));
        saverNow = computeSaver();
    }

    // ================================================================ v3.5 helpers

    private boolean isNight() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private void loadPkgSet(Set<String> set, String key) {
        set.clear();
        for (String k : prefs.getString(key, "").split("\n")) if (k.length() > 0) set.add(k);
    }

    private void savePkgSet(Set<String> set, String key) {
        StringBuilder sb = new StringBuilder();
        for (String k : set) { if (sb.length() > 0) sb.append('\n'); sb.append(k); }
        prefs.edit().putString(key, sb.toString()).apply();
    }

    /** Animation time in ms after the speed setting; 0 when animations are reduced or the saver is on. */
    private int dur(int ms) {
        if (reduceAnim || saverNow) return 0;
        return ms * 100 / animSpeed;
    }

    // ---- theme

    private void applyThemeNow() {
        themeMode = clampi(prefs.getInt("theme_mode", 0), 0, 3);
        Theme.apply(themeMode, isNight(), prefs.getInt("theme_accent", Theme.ACCENTS[0]), prefs.getInt("theme_bg", Theme.BACKGROUNDS[1]));
        drawer.setBackgroundColor(Theme.bg);
        search.setHintTextColor(Theme.a(0x88));
        search.setTextColor(Theme.text);
        if (search.getBackground() instanceof GradientDrawable) ((GradientDrawable) search.getBackground()).setColor(Theme.a(0x26));
        if (adapter != null) adapter.notifyDataSetChanged();
        if (drawerScroll == 2) rebuildDrawerPages();
        root.post(new Runnable() { public void run() { settings.rebuild(); } });
    }

    // ---- animations

    private Bundle openOptions(View from) {
        int st = (reduceAnim || saverNow) ? 3 : animOpen;
        switch (st) {
            case 1: return ActivityOptions.makeCustomAnimation(this, R.anim.v_fade_in, R.anim.v_fade_out).toBundle();
            case 2: return ActivityOptions.makeCustomAnimation(this, R.anim.v_slide_up_in, R.anim.v_hold).toBundle();
            case 3: return ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle();
            default:
                return from != null ? ActivityOptions.makeScaleUpAnimation(from, 0, 0, from.getWidth(), from.getHeight()).toBundle() : null;
        }
    }

    /** Plays when you come back to the home screen from another app. */
    private void animateReturn() {
        int st = (reduceAnim || saverNow) ? 3 : animClose;
        if (st == 3 || root.getChildCount() == 0) return;
        View c = root.getChildAt(0);
        c.animate().cancel();
        c.setPivotX(screenW / 2f);
        c.setPivotY(c.getHeight() / 2f);
        long d = dur(260);
        if (st == 0) {
            c.setScaleX(1.12f); c.setScaleY(1.12f); c.setAlpha(0.4f);
            c.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(d).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        } else if (st == 1) {
            c.setAlpha(0f);
            c.animate().alpha(1f).setDuration(d).start();
        } else {
            c.setTranslationY(dp(80)); c.setAlpha(0.3f);
            c.animate().translationY(0).alpha(1f).setDuration(d).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        }
    }

    /** Page-scroll effect: slide (none), cube, zoom or fade, driven by how far each page is from the centre. */
    private void applyScrollFx(int x) {
        if (pagesBox == null) return;
        int style = (reduceAnim || saverNow) ? 0 : animScroll;
        float dist = getResources().getDisplayMetrics().density * 12000f;
        for (int i = 0; i < pagesBox.getChildCount(); i++) {
            View c = pagesBox.getChildAt(i);
            float off = (i * (float) screenW - x) / screenW;
            float ab = Math.abs(off);
            c.setRotationY(0f); c.setScaleX(1f); c.setScaleY(1f); c.setAlpha(1f);
            if (style == 0 || ab >= 1f) continue;
            c.setPivotY(c.getHeight() / 2f);
            if (style == 1) {
                c.setCameraDistance(dist);
                c.setPivotX(off > 0 ? 0f : screenW);
                c.setRotationY(-off * (30f + fxDepth * 0.4f));
                c.setAlpha(1f - 0.3f * ab);
            } else if (style == 2) {
                c.setPivotX(screenW / 2f);
                c.setScaleX(1f - 0.25f * ab); c.setScaleY(1f - 0.25f * ab);
                c.setAlpha(1f - 0.6f * ab);
            } else {
                c.setAlpha(1f - ab);
            }
        }
        if (fxWpScroll && wpm != null && pager.count() > 1) {
            try { wpm.setWallpaperOffsets(root.getWindowToken(), Math.max(0f, Math.min(1f, x / (float) ((pager.count() - 1) * screenW))), 0.5f); }
            catch (Exception ignored) {}
        }
    }

    private int folderText() { return folderStyle == 1 ? Color.WHITE : Theme.text; }

    private void styleFolderPanel(LinearLayout panel) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(28));
        if (folderStyle == 1) {
            bg.setColor(0x00000000);
        } else if (folderStyle == 2) {
            bg.setColors(Theme.light ? new int[]{0xFFFFFFFF, 0xFFD8DCF5} : new int[]{0xFF3A3F7A, 0xFF1B1E36});
            bg.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
            bg.setStroke(dp(3), Theme.light ? 0xFFB5BAE0 : 0xFF0E1022);
            panel.setElevation(dp(14));
        } else if (folderStyle == 3) {
            bg.setColor(Theme.panel);
            bg.setCornerRadius(dp(48));
        } else {
            bg.setColor(Theme.light ? 0xB3FFFFFF : 0x992A2E4A);
            bg.setStroke(dp(1), Theme.a(0x55));
        }
        panel.setBackground(bg);
    }

    private void animateFolderIn(final View v) {
        final int st = (reduceAnim || saverNow) ? 3 : animFolder;
        if (st == 3) return;
        v.setAlpha(0f);
        if (st == 0) { v.setScaleX(0.6f); v.setScaleY(0.6f); } else if (st == 2) v.setTranslationY(dp(70));
        v.post(new Runnable() {
            public void run() {
                v.setPivotX(v.getWidth() / 2f);
                v.setPivotY(v.getHeight() / 2f);
                android.view.ViewPropertyAnimator a = v.animate().alpha(1f).setDuration(dur(240));
                if (st == 0) a.scaleX(1f).scaleY(1f).setInterpolator(new android.view.animation.OvershootInterpolator(1.2f));
                else if (st == 2) a.translationY(0).setInterpolator(new android.view.animation.DecelerateInterpolator());
                a.start();
            }
        });
    }

    // ---- 3D effects: floating icons and tilt parallax

    private static float clampf(float v) { return Math.max(-1f, Math.min(1f, v)); }

    private final SensorEventListener tiltListener = new SensorEventListener() {
        @Override public void onAccuracyChanged(Sensor s, int a) {}
        @Override public void onSensorChanged(SensorEvent e) {
            float max = dp(2 + fxDepth * 0.14f);
            float tx = -clampf(e.values[0] / 6f) * max;
            float ty = clampf((e.values[1] - 6f) / 5f) * max * 0.6f; // y reads about 9.8 when held upright
            tiltX += (tx - tiltX) * 0.12f;
            tiltY += (ty - tiltY) * 0.12f;
            pagesBox.setTranslationX(tiltX); pagesBox.setTranslationY(tiltY);
            dock.setTranslationX(tiltX * 0.5f); dock.setTranslationY(tiltY * 0.5f);
        }
    };

    private void startFx() {
        stopFx();
        if (reduceAnim || saverNow) return;
        if (fxFloat) {
            floatAnim = ValueAnimator.ofFloat(0f, 1f);
            floatAnim.setDuration(4200);
            floatAnim.setRepeatCount(ValueAnimator.INFINITE);
            floatAnim.setInterpolator(new android.view.animation.LinearInterpolator());
            floatAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator an) {
                    float f = (Float) an.getAnimatedValue();
                    float amp = dp(2 + fxDepth * 0.05f);
                    for (int i = 0; i < floatViews.size(); i++)
                        floatViews.get(i).setTranslationY((float) Math.sin(2 * Math.PI * (f + i * 0.137)) * amp);
                }
            });
            floatAnim.start();
        }
        if (fxParallax && sensors != null && accel != null) sensors.registerListener(tiltListener, accel, SensorManager.SENSOR_DELAY_UI);
    }

    private void stopFx() {
        if (floatAnim != null) { floatAnim.cancel(); floatAnim = null; }
        if (sensors != null) sensors.unregisterListener(tiltListener);
        for (View v : floatViews) v.setTranslationY(0f);
        if (pagesBox != null) { pagesBox.setTranslationX(0f); pagesBox.setTranslationY(0f); }
        if (dock != null) { dock.setTranslationX(0f); dock.setTranslationY(0f); }
        tiltX = 0f; tiltY = 0f;
    }

    // ---- performance and battery

    private boolean computeSaver() {
        if (perfMode == 1) return true;
        if (perfMode == 2 || !autoSaver) return false;
        try {
            Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (b != null) {
                int lvl = b.getIntExtra("level", -1), sc = b.getIntExtra("scale", 100), st = b.getIntExtra("status", -1);
                boolean charging = st == 2 || st == 5;
                if (lvl >= 0 && lvl * 100 / Math.max(1, sc) <= 20 && !charging) return true;
            }
            PowerManager pmgr = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pmgr != null && pmgr.isPowerSaveMode()) return true;
        } catch (Exception ignored) {}
        return false;
    }

    private void updateSaver() {
        boolean n = computeSaver();
        if (n != saverNow) { saverNow = n; applyEffects(); }
    }

    private void recheckSaver() { saverNow = computeSaver(); applyEffects(); }

    private void applyEffects() {
        stopFx();
        if (resumed) startFx();
        applyScrollFx(pager.getScrollX());
        rebuildHome();
    }

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                if (lockOn && lockScreenOff) {
                    lockPending = true;
                    if (resumed && lockView == null) showMainLock();
                }
            } else {
                updateSaver();
            }
        }
    };

    // ---- number lock and app lock

    private void showLock(String title, int count, boolean cancellable, final Runnable ok) {
        if (lockView != null) return;
        if (drawerOpen) hideDrawerNow();
        lockCancellable = cancellable;
        lockView = new LockView(this, count, lockSpeed, title, saverNow,
                new Runnable() { public void run() { hideLock(); if (ok != null) ok.run(); } },
                cancellable ? new Runnable() { public void run() { hideLock(); } } : null);
        root.addView(lockView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        lockView.setAlpha(0f);
        lockView.animate().alpha(1f).setDuration(dur(200)).start();
    }

    private void hideLock() {
        final LockView v = lockView;
        if (v == null) return;
        lockView = null;
        v.animate().alpha(0f).setDuration(dur(180)).withEndAction(new Runnable() {
            public void run() { root.removeView(v); }
        }).start();
    }

    private void showMainLock() {
        showLock("Tap the numbers: smallest to biggest", lockCount, false, new Runnable() { public void run() { lockPending = false; } });
    }

    private boolean unlockedRecently(App a) {
        Long t = appUnlocked.get(a.pkg);
        if (t == null) return false;
        long d = applockDelay == 1 ? 60000L : (applockDelay == 2 ? 300000L : 0L);
        return d > 0 && SystemClock.uptimeMillis() - t < d;
    }

    // ---- hide apps

    private void chooseApps(String title, final Set<String> set, final String prefKey, final Runnable after) {
        final String[] labels = new String[apps.size()];
        final boolean[] checked = new boolean[apps.size()];
        for (int i = 0; i < labels.length; i++) { labels[i] = apps.get(i).label; checked[i] = set.contains(apps.get(i).pkg); }
        new AlertDialog.Builder(this).setTitle(title)
                .setMultiChoiceItems(labels, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override public void onClick(DialogInterface d, int which, boolean on) { checked[which] = on; }
                })
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        set.clear();
                        for (int i = 0; i < checked.length; i++) if (checked[i]) set.add(apps.get(i).pkg);
                        savePkgSet(set, prefKey);
                        if (after != null) after.run();
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void hideApp(App a) {
        hiddenPkgs.add(a.pkg);
        savePkgSet(hiddenPkgs, "hidden_pkgs");
        applyHidden();
        toast(a.label + " hidden. Bring it back in Voltage settings > Hide apps");
    }

    private void applyHidden() {
        if (!drawerEnabled) {
            for (int i = items.size() - 1; i >= 0; i--) {
                Item it = items.get(i);
                App a = it.type == T_APP ? byKey.get(it.key) : null;
                if (a != null && hiddenPkgs.contains(a.pkg)) items.remove(i);
            }
            addAllToHome();
            saveItems();
        }
        filter(search.getText().toString());
        rebuildHome();
    }

    // ---- backup and restore

    private void backupSettings() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "voltage-backup-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(new java.util.Date()) + ".json");
        try { startActivityForResult(i, REQ_BACKUP); } catch (Exception e) { toast("No file picker on this phone"); }
    }

    private void writeBackup(Uri uri) {
        try {
            JSONObject doc = new JSONObject(), p = new JSONObject();
            for (Map.Entry<String, ?> en : prefs.getAll().entrySet()) {
                Object v = en.getValue();
                JSONObject o = new JSONObject();
                if (v instanceof Boolean) o.put("t", "b");
                else if (v instanceof Integer) o.put("t", "i");
                else if (v instanceof Long) o.put("t", "l");
                else if (v instanceof Float) o.put("t", "f");
                else if (v instanceof String) o.put("t", "s");
                else if (v instanceof Set) {
                    o.put("t", "ss");
                    JSONArray arr = new JSONArray();
                    for (Object x : (Set<?>) v) arr.put(String.valueOf(x));
                    o.put("v", arr);
                    p.put(en.getKey(), o);
                    continue;
                } else continue;
                o.put("v", v);
                p.put(en.getKey(), o);
            }
            doc.put("app", "voltage");
            doc.put("version", 5);
            doc.put("prefs", p);
            OutputStream os = getContentResolver().openOutputStream(uri);
            os.write(doc.toString(1).getBytes("UTF-8"));
            os.close();
            toast("Backup saved");
        } catch (Exception e) {
            toast("Backup failed");
        }
    }

    private void restoreSettings() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try { startActivityForResult(i, REQ_RESTORE); } catch (Exception e) { toast("No file picker on this phone"); }
    }

    private void readRestore(Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            final JSONObject doc = new JSONObject(bo.toString("UTF-8"));
            if (!"voltage".equals(doc.optString("app")) || doc.optJSONObject("prefs") == null) { toast("That is not a Voltage backup"); return; }
            new AlertDialog.Builder(this).setTitle("Restore backup?")
                    .setMessage("This replaces your current layout, folders and settings.")
                    .setPositiveButton("Restore", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) { applyRestore(doc); }
                    }).setNegativeButton("Cancel", null).show();
        } catch (Exception e) {
            toast("Can't read that file");
        }
    }

    private void applyRestore(JSONObject doc) {
        try {
            JSONObject p = doc.getJSONObject("prefs");
            SharedPreferences.Editor ed = prefs.edit();
            ed.clear();
            Iterator<String> it = p.keys();
            while (it.hasNext()) {
                String k = it.next();
                JSONObject o = p.getJSONObject(k);
                String t = o.optString("t");
                if (t.equals("b")) ed.putBoolean(k, o.getBoolean("v"));
                else if (t.equals("i")) ed.putInt(k, o.getInt("v"));
                else if (t.equals("l")) ed.putLong(k, o.getLong("v"));
                else if (t.equals("f")) ed.putFloat(k, (float) o.getDouble("v"));
                else if (t.equals("s")) ed.putString(k, o.getString("v"));
                else if (t.equals("ss")) {
                    JSONArray a = o.getJSONArray("v");
                    Set<String> st = new HashSet<>();
                    for (int j = 0; j < a.length(); j++) st.add(a.getString(j));
                    ed.putStringSet(k, st);
                }
            }
            ed.putBoolean("wp_default_done", true);
            ed.commit();
            recreate();
        } catch (Exception e) {
            toast("Restore failed");
        }
    }

    private void resetAll() {
        new AlertDialog.Builder(this).setTitle("Reset Voltage?")
                .setMessage("Layout, folders, dock and every setting go back to defaults. Make a backup first if unsure.")
                .setPositiveButton("Reset", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        prefs.edit().clear().putBoolean("wp_default_done", true).commit();
                        recreate();
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    // ================================================================ gestures

    private String gestureLabel(String key, int def) {
        int a = clampi(prefs.getInt(key, def), 0, GESTURE_NAMES.length - 1);
        if (a == 7) {
            App app = byKey.get(prefs.getString(key + "_app", ""));
            return app != null ? "Open " + app.label : "Open an app (none chosen)";
        }
        return GESTURE_NAMES[a];
    }

    private void chooseGesture(final String key, final int def, final Runnable done) {
        new AlertDialog.Builder(this).setTitle("Choose action").setItems(GESTURE_NAMES, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                if (which == 7) {
                    pickApp("Open which app?", new AppPick() {
                        public void on(App a) {
                            prefs.edit().putInt(key, 7).putString(key + "_app", a.key).apply();
                            done.run();
                        }
                    });
                } else {
                    prefs.edit().putInt(key, which).apply();
                    done.run();
                }
            }
        }).show();
    }

    private void runGesture(String key, int def) {
        int act = clampi(prefs.getInt(key, def), 0, GESTURE_NAMES.length - 1);
        switch (act) {
            case 1: openDrawer(false); break;
            case 2: openDrawer(true); break;
            case 3: expandNotifications(); break;
            case 4: expandQuickSettings(); break;
            case 5: openSettings(); break;
            case 6: homeMenu(); break;
            case 7: {
                App a = byKey.get(prefs.getString(key + "_app", ""));
                if (a != null) launch(a, null); else toast("No app chosen for this gesture");
                break;
            }
            default: break;
        }
    }

    private void expandQuickSettings() {
        try {
            Object sb = getSystemService("statusbar");
            Class.forName("android.app.StatusBarManager").getMethod("expandSettingsPanel").invoke(sb);
        } catch (Throwable t) {
            expandNotifications();
        }
    }

    /** True when the touch is on the home pages but not on an icon, folder or widget. */
    private boolean emptyHomeSpot(MotionEvent e) {
        float x = e.getRawX(), y = e.getRawY();
        if (!hit(pager, x, y)) return false;
        View pg = pagesBox.getChildAt(pager.page());
        if (!(pg instanceof ViewGroup)) return false;
        ViewGroup g = (ViewGroup) pg;
        for (int i = 0; i < g.getChildCount(); i++) if (hit(g.getChildAt(i), x, y)) return false;
        return true;
    }

    private void refreshAllCells() {
        rebuildHome();
        if (adapter != null) adapter.notifyDataSetChanged();
        if (drawerScroll == 2) rebuildDrawerPages();
    }

    // ================================================================ drag and drop

    private void startDrag(Item it, View v) {
        if (dragItem != null) return;
        dragItem = it;
        dragIdx = items.indexOf(it);
        dragView = v;
        dragStartX = lastX;
        dragStartY = lastY;
        dragCancelSent = false;
        edgeDir = 0;

        int w = v.getWidth(), h = v.getHeight();
        if (w <= 0 || h <= 0) { w = dp(64); h = dp(64); }
        Bitmap snap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(snap));
        ghost = new ImageView(this);
        ghost.setImageBitmap(snap);
        ghost.setScaleX(1.15f);
        ghost.setScaleY(1.15f);
        ghost.setAlpha(0.92f);
        root.addView(ghost, new FrameLayout.LayoutParams(w, h));
        moveGhost();
        v.setVisibility(View.INVISIBLE);

        tInfo.setVisibility(it.type == T_APP ? View.VISIBLE : View.INVISIBLE);
        tUninstall.setVisibility(it.type == T_APP ? View.VISIBLE : View.INVISIBLE);
        targetBar.setVisibility(View.VISIBLE);
        v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
    }

    private void moveGhost() {
        if (ghost == null) return;
        int[] rl = new int[2];
        root.getLocationOnScreen(rl);
        ghost.setTranslationX(lastX - rl[0] - ghost.getWidth() / 2f);
        ghost.setTranslationY(lastY - rl[1] - ghost.getHeight() / 2f);
    }

    private int targetUnder() {
        if (targetBar.getVisibility() != View.VISIBLE) return 0;
        if (hit(tRemove, lastX, lastY)) return 1;
        if (dragItem.type == T_APP && hit(tInfo, lastX, lastY)) return 2;
        if (dragItem.type == T_APP && hit(tUninstall, lastX, lastY)) return 3;
        return 0;
    }

    private void dragMove() {
        moveGhost();
        int t = targetUnder();
        tRemove.setBackgroundColor(t == 1 ? 0x66FFFFFF : 0);
        tInfo.setBackgroundColor(t == 2 ? 0x66FFFFFF : 0);
        tUninstall.setBackgroundColor(t == 3 ? 0x66FFFFFF : 0);
        int dir = lastX < dp(36) ? -1 : (lastX > screenW - dp(36) ? 1 : 0);
        if (dir != edgeDir) {
            edgeDir = dir;
            root.removeCallbacks(edgeRun);
            if (dir != 0) root.postDelayed(edgeRun, 500);
        }
    }

    private void endDrag() {
        root.removeCallbacks(edgeRun);
        edgeDir = 0;
        if (ghost != null) { root.removeView(ghost); ghost = null; }
        if (dragView != null) dragView.setVisibility(View.VISIBLE);
        targetBar.setVisibility(View.GONE);
        tRemove.setBackgroundColor(0);
        tInfo.setBackgroundColor(0);
        tUninstall.setBackgroundColor(0);
        dragItem = null;
        dragView = null;
    }

    private void dragDrop() {
        final Item it = dragItem;
        boolean moved = Math.hypot(lastX - dragStartX, lastY - dragStartY) > dp(12);
        int tgt = targetUnder();
        float[] cell = moved ? locateCell() : null;
        endDrag();

        if (tgt == 1) { removeItem(it); return; }
        App a = it.type == T_APP ? byKey.get(it.key) : null;
        if (tgt == 2 && a != null) { appInfo(a); return; }
        if (tgt == 3 && a != null) { uninstall(a); return; }
        if (!moved) {
            if (a != null) appMenu(a, false, true);
            else if (it.type == T_FOLDER) folderMenu(it);
            else if (it.type == T_WIDGET) widgetMenu(it);
            return;
        }
        if (cell == null) return; // dropped outside the grid: nothing changes
        dropOnGrid(it, (int) cell[0], (int) cell[1], (int) cell[2], cell[3], cell[4]);
    }

    /** Returns {page, row, col, fracX, fracY} for the finger position, or null if outside the page area. */
    private float[] locateCell() {
        int[] loc = new int[2];
        pager.getLocationOnScreen(loc);
        float ry = lastY - loc[1];
        if (ry < 0 || ry > pager.getHeight() || lastX < loc[0] || lastX > loc[0] + pager.getWidth()) return null;
        float rx = lastX - loc[0] + pager.getScrollX();
        int page = (int) (rx / screenW);
        if (page < 0 || page >= pager.count()) return null;
        float inX = rx - page * screenW - pagePad();
        float cx = inX / cellW(), cy = ry / cellH();
        int col = Math.max(0, Math.min(COLS - 1, (int) Math.floor(cx)));
        int row = Math.max(0, Math.min(ROWS - 1, (int) Math.floor(cy)));
        return new float[]{page, row, col, cx - col, cy - row};
    }

    private void dropOnGrid(Item drag, int page, int row, int col, float fx, float fy) {
        int from = items.indexOf(drag);
        if (from < 0) return;
        Item target = null;
        int tIdx = -1;
        for (int i = 0; i < items.size(); i++) {
            Item o = items.get(i);
            if (o == drag || o.page != page) continue;
            int w = o.type == T_WIDGET ? o.w : 1, h = o.type == T_WIDGET ? o.h : 1;
            if (row >= o.row && row < o.row + h && col >= o.col && col < o.col + w) { target = o; tIdx = i; break; }
        }

        // 1) drop an app onto the middle of an app or folder -> folder
        if (target != null && drag.type == T_APP && target.type != T_WIDGET
                && Math.abs(fx - 0.5f) < 0.3f && Math.abs(fy - 0.5f) < 0.3f) {
            if (target.type == T_FOLDER) {
                if (!target.keys.contains(drag.key)) target.keys.add(drag.key);
                items.remove(drag);
            } else {
                Item f = new Item();
                f.type = T_FOLDER;
                f.keys.add(target.key);
                f.keys.add(drag.key);
                items.set(tIdx, f);
                items.remove(drag);
            }
            saveItems();
            rebuildHome();
            return;
        }

        // 2) otherwise reorder
        int insertAt;
        if (target != null) {
            boolean before = fx < 0.5f;
            insertAt = before ? tIdx : tIdx + 1;
        } else {
            int last = -1;
            for (int i = 0; i < items.size(); i++) {
                Item o = items.get(i);
                if (o != drag && o.page == page) last = i;
            }
            insertAt = last >= 0 ? last + 1 : items.size();
        }
        items.remove(from);
        if (from < insertAt) insertAt--;
        insertAt = Math.max(0, Math.min(items.size(), insertAt));
        items.add(insertAt, drag);
        saveItems();
        rebuildHome();
    }

    private void removeItem(Item it) {
        if (needsDrawerToRemove(it)) return;
        items.remove(it);
        if (it.type == T_WIDGET) host.deleteAppWidgetId(it.wid);
        if (!drawerEnabled) addAllToHome();
        saveItems();
        rebuildHome();
    }

    // ================================================================ folders

    private void folderMenu(final Item f) {
        final String[] arr = {"Open folder", "Remove folder (apps stay in drawer)"};
        new AlertDialog.Builder(this).setTitle(f.name).setItems(arr, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int w) {
                if (w == 0) showFolder(f); else removeItem(f);
            }
        }).show();
    }

    private void showFolder(final Item f) {
        final Dialog dlg = new Dialog(this);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(14), dp(12), dp(14));
        styleFolderPanel(panel);

        final EditText name = new EditText(this);
        name.setText(f.name);
        name.setTextColor(folderText());
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        name.setBackground(null);
        panel.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView sv = new ScrollView(this);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        sv.addView(rows, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout row = null;
        int n = 0;
        for (final String k : new ArrayList<>(f.keys)) {
            final App a = byKey.get(k);
            if (a == null) continue;
            if (n % COLS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                rows.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            View cell = makeNamedCellC(a, folderText());
            cell.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { launch(a, null); dlg.dismiss(); }
            });
            cell.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) { folderAppMenu(dlg, f, a); return true; }
            });
            row.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            n++;
        }
        if (row != null) {
            for (int i = n % COLS; i != 0 && i < COLS; i++)
                row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        }
        panel.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dlg.setContentView(panel);
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dlg.getWindow().setLayout((int) (screenW * 0.92f), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface d) {
                String s = name.getText().toString().trim();
                if (items.contains(f)) {
                    f.name = s.isEmpty() ? "Folder" : s;
                    saveItems();
                    rebuildHome();
                }
            }
        });
        dlg.show();
        animateFolderIn(panel);
    }

    private void folderAppMenu(final Dialog dlg, final Item f, final App a) {
        final String[] arr = {"Move out of folder", "App info", "Uninstall"};
        new AlertDialog.Builder(this).setTitle(a.label).setItems(arr, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int w) {
                if (w == 0) {
                    int idx = items.indexOf(f);
                    f.keys.remove(a.key);
                    if (idx >= 0) {
                        int at = idx + 1;
                        if (f.keys.size() <= 1) {
                            if (f.keys.size() == 1) items.set(idx, appItem(f.keys.get(0)));
                            else { items.remove(idx); at = idx; }
                        }
                        items.add(Math.min(at, items.size()), appItem(a.key));
                    }
                    saveItems();
                    dlg.dismiss();
                    if (items.contains(f)) showFolder(f);
                } else if (w == 1) appInfo(a);
                else uninstall(a);
            }
        }).show();
    }

    // ================================================================ widgets

    private void pickWidget() {
        final List<AppWidgetProviderInfo> list = new ArrayList<>(wm.getInstalledProviders());
        if (list.isEmpty()) { Toast.makeText(this, "No widgets available", Toast.LENGTH_SHORT).show(); return; }
        final Map<AppWidgetProviderInfo, String> names = new HashMap<>();
        for (AppWidgetProviderInfo p : list) {
            String label;
            try { label = p.loadLabel(pm); } catch (Exception e) { label = p.provider.getClassName(); }
            String appName = p.provider.getPackageName();
            try { appName = pm.getApplicationLabel(pm.getApplicationInfo(p.provider.getPackageName(), 0)).toString(); } catch (Exception ignored) {}
            names.put(p, appName + " - " + label);
        }
        Collections.sort(list, new Comparator<AppWidgetProviderInfo>() {
            @Override public int compare(AppWidgetProviderInfo x, AppWidgetProviderInfo y) {
                return names.get(x).compareToIgnoreCase(names.get(y));
            }
        });
        String[] arr = new String[list.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = names.get(list.get(i));
        new AlertDialog.Builder(this).setTitle("Add widget").setItems(arr, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int w) { bindWidget(list.get(w)); }
        }).show();
    }

    private void bindWidget(AppWidgetProviderInfo info) {
        int id = host.allocateAppWidgetId();
        pendingWid = id;
        boolean ok = false;
        try { ok = wm.bindAppWidgetIdIfAllowed(id, info.provider); } catch (Exception ignored) {}
        if (ok) { afterBind(id, info); return; }
        Intent i = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
        try { startActivityForResult(i, REQ_BIND); }
        catch (Exception e) { host.deleteAppWidgetId(id); Toast.makeText(this, "Can't add this widget", Toast.LENGTH_SHORT).show(); }
    }

    private void afterBind(int id, AppWidgetProviderInfo info) {
        if (info.configure != null) {
            try { host.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIG, null); return; }
            catch (Exception ignored) { /* fall through and add without configuring */ }
        }
        addWidgetItem(id, info);
    }

    private void addWidgetItem(int id, AppWidgetProviderInfo info) {
        Item w = new Item();
        w.type = T_WIDGET;
        w.wid = id;
        float cw = cellW(), ch = cellH();
        w.w = Math.max(1, Math.min(COLS, (int) Math.ceil(info.minWidth / Math.max(1f, cw))));
        w.h = Math.max(1, Math.min(ROWS, (int) Math.ceil(info.minHeight / Math.max(1f, ch))));
        // put it on the page you are looking at: after the last item of that page
        int page = pager.page(), last = -1;
        for (int i = 0; i < items.size(); i++) if (items.get(i).page == page) last = i;
        items.add(last >= 0 ? last + 1 : items.size(), w);
        pendingWid = -1;
        saveItems();
        rebuildHome();
    }

    private void widgetMenu(final Item w) {
        if (widgetsLocked) { toast("Widgets are locked (Voltage settings > Widgets)"); return; }
        final String[] arr = {"Resize...", "Move earlier", "Move later", "Remove widget"};
        new AlertDialog.Builder(this).setItems(arr, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                int idx = items.indexOf(w);
                if (idx < 0) return;
                switch (which) {
                    case 0: resizeWidget(w); return;
                    case 1:
                        if (idx > 0) { items.remove(idx); items.add(idx - 1, w); }
                        break;
                    case 2:
                        if (idx < items.size() - 1) { items.remove(idx); items.add(idx + 1, w); }
                        break;
                    default: removeItem(w); return;
                }
                saveItems();
                rebuildHome();
            }
        }).show();
    }

    private void resizeWidget(final Item w) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        LinearLayout box = new LinearLayout(b.getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(12), dp(24), dp(4));
        box.addView(stepRow(b.getContext(), "Width (columns)", w, true));
        box.addView(stepRow(b.getContext(), "Height (rows)", w, false));
        b.setTitle("Resize widget").setView(box).setPositiveButton("Done", null).show();
    }

    private View stepRow(Context c, String label, Item w, boolean horiz) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));
        TextView l = new TextView(c);
        l.setText(label);
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = new TextView(c);
        v.setText(String.valueOf(horiz ? w.w : w.h));
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        v.setGravity(Gravity.CENTER);
        v.setMinWidth(dp(36));
        row.addView(stepBtn(c, "\u2212", -1, w, horiz, v));
        row.addView(v);
        row.addView(stepBtn(c, "+", 1, w, horiz, v));
        return row;
    }

    private Button stepBtn(Context c, String s, final int delta, final Item w, final boolean horiz, final TextView v) {
        Button b = new Button(c);
        b.setText(s);
        b.setMinWidth(dp(48));
        b.setMinimumWidth(dp(48));
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) {
                int cur = horiz ? w.w : w.h;
                int n = clampi(cur + delta, 1, horiz ? COLS : ROWS);
                if (n == cur) return;
                if (horiz) w.w = n; else w.h = n;
                v.setText(String.valueOf(n));
                saveItems();
                rebuildHome();
            }
        });
        return b;
    }

    // ================================================================ drawer

    private void filter(String q) {
        shown.clear();
        String s = q.trim().toLowerCase();
        boolean searchOnly = !drawerEnabled;
        for (App a : apps) {
            boolean hid = hiddenPkgs.contains(a.pkg) && (s.isEmpty() || hideSearch);
            if (!hid && (s.isEmpty() ? !searchOnly : a.label.toLowerCase().contains(s))) shown.add(a);
        }
        adapter.notifyDataSetChanged();
        if (drawerScroll == 2) rebuildDrawerPages();
        updateExtras(s);
    }

    private boolean gridAtTop() {
        if (drawerScroll == 2 || grid.getChildCount() == 0) return true;
        return grid.getFirstVisiblePosition() == 0 && grid.getChildAt(0).getTop() >= 0;
    }

    private void applyDrawerConfig() {
        grid.setNumColumns(drawerCols);
        grid.setFastScrollEnabled(drawerScroll == 1);
        boolean paged = drawerScroll == 2;
        grid.setVisibility(paged ? View.GONE : View.VISIBLE);
        drawerPager.setVisibility(paged ? View.VISIBLE : View.GONE);
    }

    /** Horizontal pages of apps (scroll style "Horizontal pages"). */
    private void rebuildDrawerPages() {
        int w = drawerPager.getWidth(), h = drawerPager.getHeight();
        if (w <= 0 || h <= 0) return;
        int rowsPer = Math.max(3, h / dp(100));
        int per = drawerCols * rowsPer;
        drawerPagesBox.removeAllViews();
        int pages = Math.max(1, (shown.size() + per - 1) / per);
        for (int p = 0; p < pages; p++) {
            LinearLayout page = new LinearLayout(this);
            page.setOrientation(LinearLayout.VERTICAL);
            drawerPagesBox.addView(page, new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT));
            for (int r = 0; r < rowsPer; r++) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                page.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
                for (int c = 0; c < drawerCols; c++) {
                    int idx = p * per + r * drawerCols + c;
                    View cell;
                    if (idx < shown.size()) {
                        final App a = shown.get(idx);
                        cell = makeNamedCell(a);
                        cell.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { launch(a, v); } });
                        cell.setOnLongClickListener(new View.OnLongClickListener() {
                            @Override public boolean onLongClick(View v) { appMenu(a, true, false); return true; }
                        });
                    } else {
                        cell = new View(this);
                    }
                    row.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
                }
            }
        }
        drawerPager.setPages(pages, w);
        drawerPager.goToNow(0);
    }

    // ---- search: settings and contacts under the app results

    private static final String[][] PHONE_SETTINGS = {
        {"Wi-Fi", Settings.ACTION_WIFI_SETTINGS}, {"Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS},
        {"Display and brightness", Settings.ACTION_DISPLAY_SETTINGS}, {"Sound and volume", Settings.ACTION_SOUND_SETTINGS},
        {"Battery saver", Settings.ACTION_BATTERY_SAVER_SETTINGS}, {"Location", Settings.ACTION_LOCATION_SOURCE_SETTINGS},
        {"Apps", Settings.ACTION_APPLICATION_SETTINGS}, {"Storage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS},
        {"Date and time", Settings.ACTION_DATE_SETTINGS}, {"Language", Settings.ACTION_LOCALE_SETTINGS},
        {"Accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS}, {"Airplane mode", Settings.ACTION_AIRPLANE_MODE_SETTINGS},
        {"Mobile data and network", Settings.ACTION_WIRELESS_SETTINGS}, {"NFC", Settings.ACTION_NFC_SETTINGS},
        {"Security", Settings.ACTION_SECURITY_SETTINGS}, {"Default apps", Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS},
        {"Phone settings", Settings.ACTION_SETTINGS}
    };
    private static final String[] VOLTAGE_SETTINGS = {
        "Voltage settings", "Wallpaper", "Icon style", "Icon size", "Icon labels", "Home screen layout", "App drawer", "App search"
    };

    private void addExtraRow(String kind, String name, final Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Theme.a(0x1A));
        g.setCornerRadius(dp(14));
        row.setBackground(g);
        TextView k = new TextView(this);
        k.setText(kind);
        k.setTextColor(Theme.sub);
        k.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        row.addView(k, new LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView n = new TextView(this);
        n.setText(name);
        n.setTextColor(Theme.text);
        n.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(n, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { action.run(); } });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        searchExtras.addView(row, lp);
        if (firstExtra == null) firstExtra = action;
    }

    private void updateExtras(String s) {
        searchExtras.removeAllViews();
        firstExtra = null;
        if (s.isEmpty()) { searchExtras.setVisibility(View.GONE); return; }
        if (searchSettings) {
            int n = 0;
            for (final String name : VOLTAGE_SETTINGS) {
                if (n >= 2 || !name.toLowerCase().contains(s)) continue;
                addExtraRow("Voltage", name, new Runnable() { public void run() { hideDrawerNow(); openSettings(); } });
                n++;
            }
            for (final String[] e : PHONE_SETTINGS) {
                if (n >= 4) break;
                if (!e[0].toLowerCase().contains(s)) continue;
                addExtraRow("Settings", e[0], new Runnable() { public void run() { openPhoneSetting(e[1]); } });
                n++;
            }
        }
        if (searchContacts) queryContacts(s);
        searchExtras.setVisibility(searchExtras.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    private void openPhoneSetting(String action) {
        try { startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            catch (Exception e2) { Toast.makeText(this, "Not available", Toast.LENGTH_SHORT).show(); return; }
        }
        root.postDelayed(new Runnable() { public void run() { hideDrawerNow(); } }, 350);
    }

    private boolean hasContactsPermission() {
        return android.os.Build.VERSION.SDK_INT < 23
                || checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    private void queryContacts(String s) {
        if (!hasContactsPermission()) return;
        Cursor c = null;
        try {
            c = getContentResolver().query(ContactsContract.Contacts.CONTENT_URI,
                    new String[]{ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY},
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " LIKE ?", new String[]{"%" + s + "%"},
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC");
            int n = 0;
            while (c != null && c.moveToNext() && n < 3) {
                final long id = c.getLong(0);
                final String lookup = c.getString(1);
                addExtraRow("Contact", c.getString(2), new Runnable() {
                    public void run() {
                        try { startActivity(new Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.getLookupUri(id, lookup)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                        catch (Exception e) { Toast.makeText(MainActivity.this, "Can't open contact", Toast.LENGTH_SHORT).show(); return; }
                        root.postDelayed(new Runnable() { public void run() { hideDrawerNow(); } }, 350);
                    }
                });
                n++;
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
    }

    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req != REQ_CONTACTS) return;
        if (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED) {
            searchContacts = false;
            prefs.edit().putBoolean("search_contacts", false).apply();
            Toast.makeText(this, "Contacts permission denied", Toast.LENGTH_SHORT).show();
            if (settings != null) settings.sync();
        }
    }

    private void openDrawer() { openDrawer(false); }

    private void openDrawer(final boolean typing) {
        if (drawerOpen) return;
        drawerOpen = true;
        filter("");
        drawer.setAlpha(0f);
        drawer.setTranslationY(dp(120));
        drawer.setVisibility(View.VISIBLE);
        drawer.animate().alpha(1f).translationY(0).setDuration(dur(220)).start();
        if (drawerScroll == 2) drawerPager.post(new Runnable() { public void run() { rebuildDrawerPages(); } });
        if (!drawerEnabled || typing) {
            search.requestFocus();
            search.postDelayed(new Runnable() {
                public void run() {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null && drawerOpen) imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 250);
        }
    }

    private void closeDrawer() {
        if (!drawerOpen) return;
        drawerOpen = false;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        drawer.animate().alpha(0f).translationY(dp(120)).setDuration(dur(180)).withEndAction(new Runnable() {
            public void run() { if (!drawerOpen) { drawer.setVisibility(View.GONE); search.setText(""); } }
        }).start();
    }

    private void hideDrawerNow() {
        drawerOpen = false;
        drawer.setVisibility(View.GONE);
        search.setText("");
    }

    private class DrawerAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public View getView(int i, View convert, ViewGroup parent) {
            return makeNamedCell(shown.get(i));
        }
    }

    // ================================================================ pager

    /** Horizontal pages that snap to the nearest screen. */
    static class PagerHSV extends HorizontalScrollView {
        private int pages = 1, w = 1;
        private VelocityTracker vt;

        PagerHSV(Context c) { super(c); }

        void setPages(int n, int width) { pages = n; w = Math.max(1, width); }
        int count() { return pages; }
        int page() { return Math.max(0, Math.min(pages - 1, Math.round(getScrollX() / (float) w))); }
        void goTo(int p) { smoothScrollTo(p * w, 0); }
        void goToNow(int p) { scrollTo(p * w, 0); }

        @Override public void fling(int velocityX) { /* snapping is handled on touch up */ }

        @Override public boolean onTouchEvent(MotionEvent ev) {
            if (vt == null) vt = VelocityTracker.obtain();
            vt.addMovement(ev);
            int a = ev.getActionMasked();
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                boolean handled = super.onTouchEvent(ev);
                vt.computeCurrentVelocity(1000);
                float vx = vt.getXVelocity();
                vt.recycle(); vt = null;
                float pos = getScrollX() / (float) w;
                int target;
                if (vx < -700) target = (int) Math.ceil(pos);
                else if (vx > 700) target = (int) Math.floor(pos);
                else target = Math.round(pos);
                target = Math.max(0, Math.min(pages - 1, target));
                smoothScrollTo(target * w, 0);
                return handled;
            }
            return super.onTouchEvent(ev);
        }
    }
}
