package com.camon18i.voltage;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.Dialog;
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
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
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
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
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

    private static final int COLS = 4, ROWS = 5;
    private static final int REQ_BIND = 71, REQ_CONFIG = 72;

    private PackageManager pm;
    private SharedPreferences prefs;
    private final List<App> apps = new ArrayList<>();
    private final Map<String, App> byKey = new HashMap<>();
    private final Map<String, String> byComp = new HashMap<>();
    private final Map<String, String> byPkg = new HashMap<>();
    private final Map<String, String> kindPkg = new HashMap<>();
    private final Map<String, Bitmap> cache = new HashMap<>();
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
        loadIconMap();
        buildUi();
        loadApps();
        rebuildHome();

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f);

        gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null || dragItem != null) return false;
                float dy = e2.getY() - e1.getY(), dx = e2.getX() - e1.getX();
                if (Math.abs(dy) > Math.abs(dx) * 1.5f && Math.abs(vy) > 800) {
                    if (dy < 0 && !drawerOpen) { if (!startsInWidget(e1)) openDrawer(); }
                    else if (dy > 0 && drawerOpen && gridAtTop()) closeDrawer();
                    else if (dy > 0 && !drawerOpen) { if (!startsInWidget(e1)) expandNotifications(); }
                }
                return false;
            }
        });
    }

    @Override protected void onStart() {
        super.onStart();
        try { host.startListening(); } catch (Exception ignored) {}
    }

    @Override protected void onStop() {
        try { host.stopListening(); } catch (Exception ignored) {}
        super.onStop();
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(pkgReceiver); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        rebuildHome(); // refreshes the calendar date
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (dragItem != null) endDrag();
        if (drawerOpen) closeDrawer(); else pager.goTo(0);
    }

    @Override public void onBackPressed() {
        if (dragItem != null) { endDrag(); return; }
        if (drawerOpen) closeDrawer(); else pager.goTo(0);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent ev) {
        lastX = ev.getRawX();
        lastY = ev.getRawY();
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
        if (gd != null) gd.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
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
        apps.clear(); byKey.clear(); cache.clear();
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
                if (bm == null) a.iconName = null; else a.plain = bm;
            }
            if (a.iconName == null) a.plain = composeIcon(ri);
            apps.add(a);
            byKey.put(a.key, a);
        }
        Collections.sort(apps, new Comparator<App>() {
            @Override public int compare(App x, App y) { return x.label.compareToIgnoreCase(y.label); }
        });
        buildDock();
        if (apps.isEmpty() && !old.isEmpty()) return; // query failed: keep the saved layout untouched
        loadItems();
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

    private Bitmap composeIcon(ResolveInfo ri) {
        Bitmap b = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF171A33);
        c.drawRoundRect(new RectF(14, 14, 242, 242), 62, 62, p);
        try {
            Drawable d = ri.loadIcon(pm);
            if (d != null) { d.setBounds(46, 46, 210, 210); d.draw(c); }
        } catch (Exception ignored) {}
        return b;
    }

    private Bitmap calendarBitmap() {
        int day = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
        Bitmap b = res("calendar_" + day);
        return b != null ? b : res("ic_calendar");
    }

    /** A still image of the icon (used inside folders and drag shadows). */
    private Bitmap stillBitmap(App a) {
        if (a.isCal()) return calendarBitmap();
        if (a.isClock()) { Bitmap c = res("ic_clock"); return c != null ? c : res("ic_clock_face"); }
        return a.plain;
    }

    private void buildDock() {
        dockApps.clear();
        String[] order = {"ic_phone", "ic_messages", "ic_camera", "ic_chrome"};
        for (String ic : order) {
            for (App a : apps) {
                if (ic.equals(a.iconName)) { dockApps.add(a); break; }
            }
        }
        if (dockApps.size() < 4) {
            for (String ic : new String[]{"ic_gallery", "ic_settings", "ic_contacts"}) {
                if (dockApps.size() >= 4) break;
                for (App a : apps) { if (ic.equals(a.iconName) && !dockApps.contains(a)) { dockApps.add(a); break; } }
            }
        }
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

    private void unpin(App a) {
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
            @Override public void onScrollChange(View v, int x, int y, int ox, int oy) { updateDots(); }
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
        GradientDrawable dbg = new GradientDrawable();
        dbg.setColor(0x44FFFFFF);
        dbg.setCornerRadius(dp(30));
        dock.setBackground(dbg);
        dock.setPadding(dp(6), dp(8), dp(6), dp(8));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.setMargins(dp(12), dp(4), dp(12), dp(8));
        col.addView(dock, dlp);

        root.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- drawer
        drawer = new FrameLayout(this);
        drawer.setBackgroundColor(0xF20A0B14);
        drawer.setVisibility(View.GONE);
        LinearLayout dcol = new LinearLayout(this);
        dcol.setOrientation(LinearLayout.VERTICAL);
        dcol.setPadding(dp(8), dp(16), dp(8), 0);

        search = new EditText(this);
        search.setHint("Search apps");
        search.setHintTextColor(0x88FFFFFF);
        search.setTextColor(Color.WHITE);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_GO);
        search.setPadding(dp(18), dp(10), dp(18), dp(10));
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(0x26FFFFFF);
        sbg.setCornerRadius(dp(26));
        search.setBackground(sbg);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.setMargins(dp(8), 0, dp(8), dp(8));
        dcol.addView(search, slp);

        grid = new GridView(this);
        grid.setNumColumns(COLS);
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
        dcol.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
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

    private View makeIcon(App a, int size) {
        View v;
        if (a.isClock()) {
            v = new ClockView(this, res("ic_clock_face"), res("ic_clock_hour"), res("ic_clock_min"), res("ic_clock_sec"));
        } else {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setImageBitmap(a.isCal() ? calendarBitmap() : a.plain);
            v = iv;
        }
        v.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return v;
    }

    private View makeCellWith(View icon, String label) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(2), dp(6), dp(2), dp(6));
        c.addView(icon);
        if (label != null) {
            TextView t = new TextView(this);
            t.setText(label);
            t.setTextColor(Color.WHITE);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setGravity(Gravity.CENTER);
            t.setShadowLayer(4, 0, 1, 0xCC000000);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setPadding(0, dp(4), 0, 0);
            c.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return c;
    }

    private View makeCell(App a, boolean label) {
        return makeCellWith(makeIcon(a, dp(label ? 58 : 62)), label ? a.label : null);
    }

    private Bitmap folderBitmap(Item f) {
        Bitmap b = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setColor(0x66FFFFFF);
        c.drawRoundRect(new RectF(14, 14, 242, 242), 62, 62, p);
        p.setColor(Color.WHITE);
        int n = Math.min(4, f.keys.size());
        for (int i = 0; i < n; i++) {
            App a = byKey.get(f.keys.get(i));
            if (a == null) continue;
            Bitmap ic = stillBitmap(a);
            if (ic == null) continue;
            int x = 34 + (i % 2) * 100, y = 34 + (i / 2) * 100;
            c.drawBitmap(ic, null, new RectF(x, y, x + 88, y + 88), p);
        }
        return b;
    }

    private View makeFolderCell(Item f) {
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(folderBitmap(f));
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(58), dp(58)));
        return makeCellWith(iv, f.name);
    }

    private float pagePad() { return dp(6); }
    private float cellW() { return (screenW - 2 * pagePad()) / COLS; }
    private float cellH() { return pager.getHeight() / (float) ROWS; }

    private void rebuildHome() {
        if (pagesBox == null || pager.getHeight() <= 0 || dragItem != null) return;
        int curPage = pager.page();
        pagesBox.removeAllViews();
        widgetViews.clear();
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
                int wpx = (int) (it.w * cw) - dp(8), hpx = (int) (it.h * ch) - dp(8);
                AppWidgetHostView hv;
                try { hv = host.createView(this, it.wid, info); } catch (Exception e) { continue; }
                float dens = getResources().getDisplayMetrics().density;
                int wdp = (int) (wpx / dens), hdp = (int) (hpx / dens);
                try { hv.updateAppWidgetSize(null, wdp, hdp, wdp, hdp); } catch (Exception ignored) {}
                hv.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override public boolean onLongClick(View v) { widgetMenu(it); return true; }
                });
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(wpx, hpx);
                lp.leftMargin = left + dp(4);
                lp.topMargin = top + dp(4);
                page.addView(hv, lp);
                widgetViews.add(hv);

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
                mlp.leftMargin = left + dp(4) + wpx - dp(32);
                mlp.topMargin = top + dp(8);
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
        }

        pager.setPages(pageCount, screenW);
        final int target = Math.min(curPage, pageCount - 1);
        pager.post(new Runnable() { public void run() { pager.goToNow(target); updateDots(); } });

        dock.removeAllViews();
        for (final App a : dockApps) {
            View cell = makeCell(a, false);
            cell.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { launch(a, v); } });
            cell.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) { appMenu(a, false, false); return true; }
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

    private void launch(App a, View from) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(a.cn).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            Bundle o = null;
            if (from != null) o = ActivityOptions.makeScaleUpAnimation(from, 0, 0, from.getWidth(), from.getHeight()).toBundle();
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
        list.add("App info");
        list.add("Uninstall");
        final String[] arr = list.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle(a.label).setItems(arr, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int w) {
                String s = arr[w];
                if (s.startsWith("Add")) pin(a);
                else if (s.startsWith("Remove")) unpin(a);
                else if (s.equals("App info")) appInfo(a);
                else uninstall(a);
            }
        }).show();
    }

    private void homeMenu() {
        final String[] arr = {"Add widget", "Change wallpaper", "Open all apps", "Phone settings"};
        new AlertDialog.Builder(this).setItems(arr, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int w) {
                try {
                    if (w == 0) pickWidget();
                    else if (w == 1) startActivity(Intent.createChooser(new Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper"));
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
        items.remove(it);
        if (it.type == T_WIDGET) host.deleteAppWidgetId(it.wid);
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
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF01B1E36);
        bg.setCornerRadius(dp(28));
        panel.setBackground(bg);

        final EditText name = new EditText(this);
        name.setText(f.name);
        name.setTextColor(Color.WHITE);
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
            View cell = makeCell(a, true);
            cell.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { launch(a, v); dlg.dismiss(); }
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
        final String[] arr = {"Remove widget", "Wider", "Narrower", "Taller", "Shorter", "Move earlier", "Move later"};
        new AlertDialog.Builder(this).setItems(arr, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                int idx = items.indexOf(w);
                if (idx < 0) return;
                switch (which) {
                    case 0: removeItem(w); return;
                    case 1: w.w = Math.min(COLS, w.w + 1); break;
                    case 2: w.w = Math.max(1, w.w - 1); break;
                    case 3: w.h = Math.min(ROWS, w.h + 1); break;
                    case 4: w.h = Math.max(1, w.h - 1); break;
                    case 5:
                        if (idx > 0) { items.remove(idx); items.add(idx - 1, w); }
                        break;
                    default:
                        if (idx < items.size() - 1) { items.remove(idx); items.add(idx + 1, w); }
                        break;
                }
                saveItems();
                rebuildHome();
            }
        }).show();
    }

    // ================================================================ drawer

    private void filter(String q) {
        shown.clear();
        String s = q.trim().toLowerCase();
        for (App a : apps) if (s.isEmpty() || a.label.toLowerCase().contains(s)) shown.add(a);
        adapter.notifyDataSetChanged();
    }

    private boolean gridAtTop() {
        if (grid.getChildCount() == 0) return true;
        return grid.getFirstVisiblePosition() == 0 && grid.getChildAt(0).getTop() >= 0;
    }

    private void openDrawer() {
        if (drawerOpen) return;
        drawerOpen = true;
        filter("");
        drawer.setAlpha(0f);
        drawer.setTranslationY(dp(120));
        drawer.setVisibility(View.VISIBLE);
        drawer.animate().alpha(1f).translationY(0).setDuration(220).start();
    }

    private void closeDrawer() {
        if (!drawerOpen) return;
        drawerOpen = false;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        drawer.animate().alpha(0f).translationY(dp(120)).setDuration(180).withEndAction(new Runnable() {
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
            return makeCell(shown.get(i), true);
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
