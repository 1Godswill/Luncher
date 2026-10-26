package com.camon18i.voltage;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Full-screen App freezer.
 *  - "Frozen" tab: tap an app to launch it (it is woken for the launch and frozen again later, it stays in this list),
 *    tick apps and "Unfreeze selected", or "Unfreeze all".
 *  - "Freeze apps" tab: tick any number of apps and "Freeze selected".
 */
final class FreezerScreen {

    interface Host {
        void launch(String pkg, String key, String label);   // opens an app (handles the wake-up and app lock)
        void changed();                                       // the frozen list changed: reload home and drawer
    }

    private static class Row {
        String pkg, label, key, cls, sub;
        boolean system;
        ResolveInfo ri;
    }

    private static FreezerScreen current;

    static void dismissCurrent() {
        FreezerScreen c = current;
        if (c != null) { try { c.dlg.dismiss(); } catch (Exception ignored) {} }
    }

    static void show(Activity a, Host h) {
        dismissCurrent();
        new FreezerScreen(a, h);
    }

    // not final on purpose: the listeners built in the constructor use them (avoids definite-assignment errors)
    private Activity act;
    private Host host;
    private Freezer fz;
    private PackageManager pm;
    private Dialog dlg;
    private ListView list;
    private BaseAdapter adapter;
    private TextView statusTv, awakeBtn, tabFrozen, tabApps, btnMain, btnAll, sysChip;
    private EditText search;
    private final List<Row> all = new ArrayList<>(), shown = new ArrayList<>();
    private final Set<String> sel = new HashSet<>();
    private final Map<String, Drawable> icons = new HashMap<>();
    private int tab = 0;               // 0 = frozen apps, 1 = freeze apps
    private boolean showSystem = false, busy = false;

    private int dp(float v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, act.getResources().getDisplayMetrics()); }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(act);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable box(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView chip(String label) {
        TextView t = text(label, 13, Theme.text, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), dp(9), dp(14), dp(9));
        t.setBackground(box(Theme.a(0x24), 14));
        return t;
    }

    private void toast(String s) { Toast.makeText(act, s, Toast.LENGTH_LONG).show(); }

    private FreezerScreen(Activity a, Host h) {
        act = a;
        host = h;
        fz = Freezer.get(a);
        pm = a.getPackageManager();
        current = this;
        int solid = (Theme.bg & 0x00FFFFFF) | 0xFF000000;

        dlg = new Dialog(a, android.R.style.Theme_Material_NoActionBar);
        LinearLayout root = new LinearLayout(a) {
            @Override public void onWindowFocusChanged(boolean hasFocus) {
                super.onWindowFocusChanged(hasFocus);
                if (hasFocus) reload();
            }
        };
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(solid);
        root.setPadding(dp(14), dp(10), dp(14), dp(8));

        // ---- header
        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("App freezer", 22, Theme.text, true), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView setup = chip("Set up");
        setup.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showSetup(); } });
        head.addView(setup);
        TextView close = text("\u2715", 20, Theme.text, true);
        close.setPadding(dp(16), dp(6), dp(6), dp(6));
        close.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { dlg.dismiss(); } });
        head.addView(close);
        root.addView(head, new LinearLayout.LayoutParams(-1, -2));

        statusTv = text("", 12, Theme.sub, false);
        statusTv.setPadding(0, dp(6), 0, dp(4));
        root.addView(statusTv, new LinearLayout.LayoutParams(-1, -2));

        awakeBtn = chip("");
        awakeBtn.setBackground(box(Theme.accentA(0x33), 14));
        awakeBtn.setVisibility(View.GONE);
        awakeBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { refreezeNow(); } });
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2);
        alp.bottomMargin = dp(6);
        root.addView(awakeBtn, alp);

        // ---- tabs
        LinearLayout tabs = new LinearLayout(a);
        tabFrozen = chip("Frozen");
        tabApps = chip("Freeze apps");
        tabFrozen.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setTab(0); } });
        tabApps.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setTab(1); } });
        LinearLayout.LayoutParams t1 = new LinearLayout.LayoutParams(0, -2, 1f);
        t1.rightMargin = dp(8);
        tabs.addView(tabFrozen, t1);
        tabs.addView(tabApps, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(tabs, new LinearLayout.LayoutParams(-1, -2));

        // ---- search
        search = new EditText(a);
        search.setHint("Search apps");
        search.setHintTextColor(Theme.sub);
        search.setTextColor(Theme.text);
        search.setSingleLine(true);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        search.setPadding(dp(14), dp(10), dp(14), dp(10));
        search.setBackground(box(Theme.a(0x1A), 14));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int x, int y, int z) {}
            @Override public void onTextChanged(CharSequence s, int x, int y, int z) { applyFilter(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = dp(10);
        root.addView(search, slp);

        // ---- select tools
        LinearLayout tools = new LinearLayout(a);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        TextView selAll = chip("Select all");
        selAll.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { selectAllShown(); } });
        TextView selNone = chip("Clear");
        selNone.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { sel.clear(); adapter.notifyDataSetChanged(); refreshBar(); } });
        sysChip = chip("System apps: off");
        sysChip.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSystem = !showSystem; reload(); }
        });
        LinearLayout.LayoutParams tl1 = new LinearLayout.LayoutParams(-2, -2);
        tl1.rightMargin = dp(8);
        LinearLayout.LayoutParams tl2 = new LinearLayout.LayoutParams(-2, -2);
        tl2.rightMargin = dp(8);
        tools.addView(selAll, tl1);
        tools.addView(selNone, tl2);
        tools.addView(sysChip);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = dp(8);
        root.addView(tools, tlp);

        // ---- list
        list = new ListView(a);
        list.setDivider(null);
        list.setVerticalScrollBarEnabled(false);
        adapter = new BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int i) { return shown.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View convert, ViewGroup parent) { return rowView(shown.get(i)); }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                if (pos < 0 || pos >= shown.size()) return;
                Row r = shown.get(pos);
                if (tab == 0) launchRow(r); else toggle(r.pkg);
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                if (pos >= 0 && pos < shown.size()) toggle(shown.get(pos).pkg);
                return true;
            }
        });
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1, 0, 1f);
        llp.topMargin = dp(6);
        root.addView(list, llp);

        // ---- bottom bar
        LinearLayout bar = new LinearLayout(a);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        btnMain = chip("");
        btnMain.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { mainAction(); } });
        btnAll = chip("Unfreeze all");
        btnAll.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { unfreezeAll(); } });
        LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0, -2, 1f);
        b1.rightMargin = dp(8);
        bar.addView(btnMain, b1);
        bar.addView(btnAll, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = dp(8);
        root.addView(bar, blp);

        dlg.setContentView(root);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setBackgroundDrawable(new ColorDrawable(solid));
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            w.setStatusBarColor(solid);
            w.setNavigationBarColor(solid);
        }
        dlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface d) { if (current == FreezerScreen.this) current = null; }
        });
        paintTabs();
        reload();
        dlg.show();
    }

    // ================================================================ data

    private void setTab(int t) {
        if (tab == t) return;
        tab = t;
        sel.clear();
        search.setText("");
        paintTabs();
        reload();
    }

    private void paintTabs() {
        tabFrozen.setBackground(box(tab == 0 ? Theme.accentA(0x44) : Theme.a(0x24), 14));
        tabApps.setBackground(box(tab == 1 ? Theme.accentA(0x44) : Theme.a(0x24), 14));
        tabFrozen.setText("Frozen (" + fz.count() + ")");
        sysChip.setVisibility(tab == 1 ? View.VISIBLE : View.GONE);
        sysChip.setText(showSystem ? "System apps: on" : "System apps: off");
    }

    private void reload() {
        fz.sync();
        all.clear();
        if (tab == 0) {
            for (String[] r : fz.records()) {
                Row w = new Row();
                w.pkg = r[0];
                w.label = r[1];
                w.key = r[2];
                if (fz.isSoft(w.pkg)) w.sub = "Frozen (soft)";
                else if (fz.isThawed(w.pkg)) w.sub = fz.rule() == 1 ? "Awake: freezes when the screen turns off or after you go home" : "Awake: freezes when the screen turns off";
                else w.sub = "Frozen";
                all.add(w);
            }
        } else {
            Set<String> prot = fz.protectedSet();
            Set<String> seen = new HashSet<>();
            Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
                if (ri.activityInfo == null) continue;
                String p = ri.activityInfo.packageName;
                if (prot.contains(p) || fz.isFrozen(p) || !seen.add(p)) continue;
                boolean sys = ri.activityInfo.applicationInfo != null
                        && (ri.activityInfo.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
                if (sys && !showSystem) continue;
                Row w = new Row();
                w.pkg = p;
                w.cls = ri.activityInfo.name;
                w.key = p + "/" + w.cls;
                CharSequence lab = ri.loadLabel(pm);
                w.label = lab == null ? p : lab.toString();
                w.sub = sys ? "System app" : p;
                w.system = sys;
                w.ri = ri;
                all.add(w);
            }
        }
        Collections.sort(all, new Comparator<Row>() {
            @Override public int compare(Row x, Row y) { return x.label.compareToIgnoreCase(y.label); }
        });
        Set<String> have = new HashSet<>();
        for (Row r : all) have.add(r.pkg);
        sel.retainAll(have);
        paintTabs();
        applyFilter();
        refreshStatus();
    }

    private void applyFilter() {
        String q = search.getText().toString().trim().toLowerCase();
        shown.clear();
        for (Row r : all) if (q.isEmpty() || r.label.toLowerCase().contains(q) || r.pkg.toLowerCase().contains(q)) shown.add(r);
        adapter.notifyDataSetChanged();
        refreshBar();
    }

    private void refreshStatus() {
        statusTv.setText(busy ? "Working..." : fz.status());
        int n = fz.thawedCount();
        if (n > 0) {
            awakeBtn.setText(n + (n == 1 ? " app is awake" : " apps are awake") + ". Tap to freeze again now");
            awakeBtn.setVisibility(View.VISIBLE);
        } else {
            awakeBtn.setVisibility(View.GONE);
        }
    }

    private void refreshBar() {
        int n = sel.size();
        boolean frozenTab = tab == 0;
        btnMain.setText(frozenTab ? "Unfreeze selected (" + n + ")" : "Freeze selected (" + n + ")");
        btnMain.setAlpha(n > 0 && !busy ? 1f : 0.45f);
        btnMain.setBackground(box(n > 0 ? Theme.accentA(0x55) : Theme.a(0x24), 14));
        btnAll.setVisibility(frozenTab ? View.VISIBLE : View.GONE);
        btnAll.setAlpha(fz.count() > 0 && !busy ? 1f : 0.45f);
    }

    private Drawable iconFor(Row r) {
        Drawable d = icons.get(r.pkg);
        if (d == null) {
            try { d = r.ri != null ? r.ri.loadIcon(pm) : fz.iconDrawable(r.pkg); } catch (Throwable t) { d = null; }
            if (d == null) d = pm.getDefaultActivityIcon();
            icons.put(r.pkg, d);
        }
        return d;
    }

    private View rowView(final Row r) {
        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(8), dp(8), dp(8));
        if (sel.contains(r.pkg)) row.setBackground(box(Theme.accentA(0x22), 14));
        ImageView iv = new ImageView(act);
        iv.setImageDrawable(iconFor(r));
        row.addView(iv, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(r.label, 16, Theme.text, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        TextView sub = text(r.sub == null ? "" : r.sub, 12, Theme.sub, false);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(name);
        col.addView(sub);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -2, 1f);
        clp.leftMargin = dp(12);
        row.addView(col, clp);
        CheckBox cb = new CheckBox(act);
        cb.setFocusable(false);
        cb.setChecked(sel.contains(r.pkg));
        cb.setButtonTintList(ColorStateList.valueOf(Theme.accent));
        cb.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (((CheckBox) v).isChecked()) sel.add(r.pkg); else sel.remove(r.pkg);
                adapter.notifyDataSetChanged();
                refreshBar();
            }
        });
        row.addView(cb);
        return row;
    }

    private void toggle(String pkg) {
        if (!sel.remove(pkg)) sel.add(pkg);
        adapter.notifyDataSetChanged();
        refreshBar();
    }

    private void selectAllShown() {
        for (Row r : shown) sel.add(r.pkg);
        adapter.notifyDataSetChanged();
        refreshBar();
    }

    // ================================================================ actions

    private void launchRow(Row r) {
        host.launch(r.pkg, r.key, r.label);
        list.postDelayed(new Runnable() { @Override public void run() { if (dlg.isShowing()) reload(); } }, 2500);
    }

    private void mainAction() {
        if (busy || sel.isEmpty()) return;
        if (tab == 0) unfreezeSelected(); else freezeSelected();
    }

    private void freezeSelected() {
        final List<String[]> t = new ArrayList<>();
        boolean sys = false;
        for (Row r : all) {
            if (!sel.contains(r.pkg)) continue;
            t.add(new String[]{r.pkg, r.label, r.key});
            if (r.system) sys = true;
        }
        if (t.isEmpty()) return;
        if (!sys) { runFreeze(t); return; }
        new AlertDialog.Builder(act).setTitle("Freeze system apps?")
                .setMessage("Some selected apps are system apps. Freezing the wrong one can break calls, messages, notifications or the camera. You can unfreeze them here at any time.")
                .setPositiveButton("Freeze", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { runFreeze(t); }
                }).setNegativeButton("Cancel", null).show();
    }

    private void runFreeze(List<String[]> t) {
        busy = true;
        refreshStatus();
        refreshBar();
        fz.freeze(t, new Freezer.Done() {
            @Override public void done(boolean ok, String msg) { finished(msg); }
        });
    }

    private void unfreezeSelected() {
        List<String> p = new ArrayList<>(sel);
        runUnfreeze(p);
    }

    private void unfreezeAll() {
        if (busy || fz.count() == 0) return;
        new AlertDialog.Builder(act).setTitle("Unfreeze all apps?")
                .setMessage("All " + fz.count() + " frozen apps will be switched on and removed from this list.")
                .setPositiveButton("Unfreeze all", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        List<String> p = new ArrayList<>();
                        for (String[] r : fz.records()) p.add(r[0]);
                        runUnfreeze(p);
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private void runUnfreeze(List<String> p) {
        if (p.isEmpty()) return;
        busy = true;
        refreshStatus();
        refreshBar();
        fz.unfreeze(p, new Freezer.Done() {
            @Override public void done(boolean ok, String msg) { finished(msg); }
        });
    }

    private void refreezeNow() {
        if (busy) return;
        busy = true;
        refreshStatus();
        fz.refreezeThawed(new Freezer.Done() {
            @Override public void done(boolean ok, String msg) { finished(msg); }
        });
    }

    private void finished(String msg) {
        busy = false;
        if (msg != null && msg.length() > 0) toast(msg);
        sel.clear();
        host.changed();
        if (dlg.isShowing()) reload();
    }

    // ================================================================ set up

    private void showSetup() {
        final String[] items = {"Allow FEG Launcher in Shizuku", "Open Shizuku", "Get Shizuku (website)", "Check root again"};
        new AlertDialog.Builder(act).setTitle("Real freezing needs Shizuku or root")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        if (w == 0) {
                            if (!Freezer.shizukuRunning()) toast("Shizuku is not running. Open Shizuku and tap Start first.");
                            else if (Freezer.shizukuAllowed()) toast("FEG Launcher is already allowed in Shizuku.");
                            else if (!Freezer.shizukuAsk()) toast("Could not ask Shizuku. Open Shizuku and allow FEG Launcher there.");
                            list.postDelayed(new Runnable() { @Override public void run() { refreshStatus(); } }, 1500);
                        } else if (w == 1) {
                            Intent i = pm.getLaunchIntentForPackage("moe.shizuku.privileged.api");
                            if (i == null) toast("Shizuku is not installed.");
                            else { try { act.startActivity(i); } catch (Exception e) { toast("Can't open Shizuku."); } }
                        } else if (w == 2) {
                            try { act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))); }
                            catch (Exception e) { toast("No browser found."); }
                        } else {
                            toast("Checking root...");
                            fz.recheckRoot(new Runnable() { @Override public void run() { refreshStatus(); } });
                        }
                    }
                }).setNegativeButton("Close", null).show();
    }
}
