package com.camon18i.voltage;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import java.util.ArrayList;
import java.util.List;
import android.widget.TextView;

/** Full-screen Voltage settings: wallpaper, icon style, icon size. Add more cards in build(). */
public class SettingsPanel extends FrameLayout {

    interface Host {
        int iconStyle();
        int iconSize();           // percent, 70..130
        int blur();               // percent, 0..100
        void setIconStyle(int style);
        void setIconSize(int percent);
        void setBlur(int percent);
        Bitmap sample(int style);
        Bitmap defaultThumb(int index);
        void useDefault(int index);
        void pickGallery();
        void openLive();
        void openCrop();
        void close();
        void addWidget();
        void chooseDock();
        String gestureLabel(String key, int def);
        void chooseGesture(String key, int def, Runnable done);
        boolean badgeAccess();
        void openBadgeAccess();
        void chooseHidden();
        void chooseProtected();
        void backup();
        void restore();
        void resetAll();
        void lockNow();
        void lockPreview();
        int geti(String key, int def);
        boolean getb(String key, boolean def);
        void puti(String key, int value);
        void putb(String key, boolean value);
    }

    static final int SIZE_MIN = 70, SIZE_MAX = 130;

    private final Host host;
    private final LinearLayout[] styleCells = new LinearLayout[IconStyler.NAMES.length];
    private ScrollView scroll;
    private final List<Runnable> syncers = new ArrayList<>();
    private boolean syncing = false;
    private TextView badgeStatus;

    public SettingsPanel(Context c, Host host) {
        super(c);
        this.host = host;
        setBackgroundColor(Theme.bg);
        setClickable(true);
        build();
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(getContext());
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

    private LinearLayout card(LinearLayout parent, String title) {
        TextView label = text(title, 12, Theme.sub, true);
        label.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1, -2);
        llp.setMargins(dp(6), dp(18), 0, dp(8));
        parent.addView(label, llp);
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(box(Theme.a(0x1F), 22));
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        parent.addView(card, new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    private void roundClip(View v, final float radiusDp) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(radiusDp));
            }
        });
        v.setClipToOutline(true);
    }

    private void tint(SeekBar sb) {
        sb.setProgressTintList(ColorStateList.valueOf(Theme.accent));
        sb.setThumbTintList(ColorStateList.valueOf(Theme.accent));
        sb.setProgressBackgroundTintList(ColorStateList.valueOf(Theme.a(0x44)));
    }

    private View pill(String label, final Runnable action) {
        TextView t = text(label, 14, Theme.text, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(box(Theme.a(0x2E), 16));
        t.setPadding(dp(14), dp(11), dp(14), dp(11));
        t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { action.run(); } });
        return t;
    }

    private View thumb(Bitmap b, String label, final Runnable action) {
        LinearLayout col = new LinearLayout(getContext());
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout f = new FrameLayout(getContext());
        f.setBackground(box(Theme.a(0x33), 14));
        if (b != null) {
            ImageView iv = new ImageView(getContext());
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(b);
            f.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            TextView plus = text(label.startsWith("Live") ? "\u25B6" : "+", 30, Theme.text, false);
            plus.setGravity(Gravity.CENTER);
            f.addView(plus, new FrameLayout.LayoutParams(-1, -1));
        }
        roundClip(f, 14);
        col.addView(f, new LinearLayout.LayoutParams(dp(78), dp(138)));
        TextView t = text(label, 12, Theme.text, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(6), 0, 0);
        col.addView(t, new LinearLayout.LayoutParams(dp(78), -2));
        col.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { action.run(); } });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(10);
        col.setLayoutParams(lp);
        return col;
    }

    // ---- generic rows: they read and write host prefs by key, so new settings are one line each

    private void note(LinearLayout card, String msg) {
        TextView t = text(msg, 12, Theme.sub, false);
        t.setPadding(0, dp(8), 0, 0);
        card.addView(t, new LinearLayout.LayoutParams(-1, -2));
    }

    private void toggleRow(LinearLayout card, String label, final String key, final boolean def) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = text(label, 15, Theme.text, false);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        final Switch sw = new Switch(getContext());
        sw.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{Theme.accent, 0xFFB0B4C8}));
        sw.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{Theme.accentA(0x66), Theme.a(0x44)}));
        sw.setChecked(host.getb(key, def));
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean on) { if (!syncing) host.putb(key, on); }
        });
        row.addView(sw);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(4);
        card.addView(row, lp);
        syncers.add(new Runnable() { public void run() { sw.setChecked(host.getb(key, def)); } });
    }

    private void seekRow(LinearLayout card, final String label, final String key, final int min, final int max, final int def) {
        final TextView t = text(label + "  " + host.geti(key, def), 14, Theme.text, false);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
        tl.topMargin = dp(10);
        card.addView(t, tl);
        final SeekBar sb = new SeekBar(getContext());
        sb.setMax(max - min);
        sb.setProgress(host.geti(key, def) - min);
        tint(sb);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) { t.setText(label + "  " + (p + min)); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) { host.puti(key, s.getProgress() + min); }
        });
        card.addView(sb, new LinearLayout.LayoutParams(-1, -2));
        syncers.add(new Runnable() {
            public void run() {
                int v = host.geti(key, def);
                sb.setProgress(v - min);
                t.setText(label + "  " + v);
            }
        });
    }

    private void chipsRow(LinearLayout card, final String[] names, final String key, final int def) {
        final TextView[] chips = new TextView[names.length];
        final Runnable paint = new Runnable() {
            public void run() {
                int cur = host.geti(key, def);
                for (int i = 0; i < chips.length; i++) {
                    GradientDrawable g = box(i == cur ? Theme.accentA(0x33) : Theme.a(0x14), 14);
                    if (i == cur) g.setStroke(dp(2), Theme.accent);
                    chips[i].setBackground(g);
                }
            }
        };
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView t = text(names[i], 14, Theme.text, false);
            t.setPadding(dp(14), dp(11), dp(14), dp(11));
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { host.puti(key, idx); paint.run(); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(8);
            card.addView(t, lp);
            chips[i] = t;
        }
        paint.run();
        syncers.add(paint);
    }

    /** A row that shows the current action and opens a chooser when tapped (gestures). */
    private void actionRow(LinearLayout card, String label, final String key, final int def) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(box(Theme.a(0x14), 14));
        row.addView(text(label, 15, Theme.text, false));
        final TextView val = text(host.gestureLabel(key, def), 13, Theme.accent, true);
        val.setPadding(0, dp(2), 0, 0);
        row.addView(val);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                host.chooseGesture(key, def, new Runnable() { public void run() { val.setText(host.gestureLabel(key, def)); } });
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        card.addView(row, lp);
        syncers.add(new Runnable() { public void run() { val.setText(host.gestureLabel(key, def)); } });
    }

    /** A label with a row of tappable choices (value = index of the chosen one). */
    private void segRow(LinearLayout card, String label, final String[] names, final String key, final int def) {
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
        tl.topMargin = dp(12);
        card.addView(text(label, 14, Theme.text, false), tl);
        HorizontalScrollView hs = new HorizontalScrollView(getContext());
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(getContext());
        final TextView[] chips = new TextView[names.length];
        final Runnable paint = new Runnable() {
            public void run() {
                int cur = host.geti(key, def);
                for (int i = 0; i < chips.length; i++) {
                    GradientDrawable g = box(i == cur ? Theme.accentA(0x33) : Theme.a(0x14), 14);
                    if (i == cur) g.setStroke(dp(2), Theme.accent);
                    chips[i].setBackground(g);
                }
            }
        };
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView t = text(names[i], 13, Theme.text, false);
            t.setPadding(dp(14), dp(9), dp(14), dp(9));
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { host.puti(key, idx); paint.run(); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(8);
            row.addView(t, lp);
            chips[i] = t;
        }
        paint.run();
        hs.addView(row);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.topMargin = dp(6);
        card.addView(hs, hl);
        syncers.add(paint);
    }

    /** A label with a row of colour dots (value = the colour int). */
    private void swatchRow(LinearLayout card, String label, final String key, final int[] colors, final int def) {
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
        tl.topMargin = dp(12);
        card.addView(text(label, 14, Theme.text, false), tl);
        HorizontalScrollView hs = new HorizontalScrollView(getContext());
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(getContext());
        final View[] dots = new View[colors.length];
        final Runnable paint = new Runnable() {
            public void run() {
                int cur = host.geti(key, def);
                for (int i = 0; i < dots.length; i++) {
                    GradientDrawable g = new GradientDrawable();
                    g.setShape(GradientDrawable.OVAL);
                    g.setColor(colors[i] | 0xFF000000);
                    g.setStroke(dp(3), colors[i] == cur ? Theme.text : Theme.a(0x44));
                    dots[i].setBackground(g);
                }
            }
        };
        for (int i = 0; i < colors.length; i++) {
            final int col = colors[i];
            View d = new View(getContext());
            d.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { host.puti(key, col); paint.run(); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(38), dp(38));
            lp.rightMargin = dp(10);
            row.addView(d, lp);
            dots[i] = d;
        }
        paint.run();
        hs.addView(row);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.topMargin = dp(8);
        card.addView(hs, hl);
        syncers.add(paint);
    }

    private void pillIn(LinearLayout card, String label, final Runnable action) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        card.addView(pill(label, action), lp);
    }

    /** Shows whether notification access (needed for badges) is on. */
    void refreshBadgeStatus() {
        if (badgeStatus == null) return;
        boolean on = host.badgeAccess();
        badgeStatus.setText(on ? "Notification access: ON. Badges are live." : "Notification access: OFF. Tap Allow access, then switch Voltage on.");
        badgeStatus.setTextColor(on ? 0xFF7CE0A0 : 0xFFFFB27A);
    }

    /** Re-reads every generic row from the host (used after a permission is refused). */
    void sync() {
        syncing = true;
        for (Runnable r : syncers) r.run();
        syncing = false;
    }

    private void build() {
        Context c = getContext();
        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(34), dp(16), dp(40));
        scroll.addView(col, new ViewGroup.LayoutParams(-1, -2));
        addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        // header
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("\u2039", 34, Theme.text, false);
        back.setPadding(dp(6), 0, dp(16), dp(4));
        back.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { host.close(); } });
        head.addView(back);
        head.addView(text("Voltage settings", 22, Theme.text, true));
        col.addView(head, new LinearLayout.LayoutParams(-1, -2));

        // ---- 1. wallpaper
        LinearLayout wp = card(col, "WALLPAPER");
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(c);
        row.addView(thumb(host.defaultThumb(0), "Crimson cross", new Runnable() { public void run() { host.useDefault(0); } }));
        row.addView(thumb(host.defaultThumb(1), "Sunset hill", new Runnable() { public void run() { host.useDefault(1); } }));
        row.addView(thumb(null, "Gallery", new Runnable() { public void run() { host.pickGallery(); } }));
        row.addView(thumb(null, "Live / 3D", new Runnable() { public void run() { host.openLive(); } }));
        hs.addView(row);
        wp.addView(hs, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, -2);
        plp.topMargin = dp(12);
        wp.addView(pill("Crop and position", new Runnable() { public void run() { host.openCrop(); } }), plp);

        final TextView blurLabel = text("Blur  " + host.blur() + "%", 14, Theme.text, false);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = dp(16);
        wp.addView(blurLabel, blp);
        SeekBar blur = new SeekBar(c);
        blur.setMax(100);
        blur.setProgress(host.blur());
        tint(blur);
        blur.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) { blurLabel.setText("Blur  " + p + "%"); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) { host.setBlur(s.getProgress()); }
        });
        wp.addView(blur, new LinearLayout.LayoutParams(-1, -2));

        // ---- 2. icon style
        LinearLayout st = card(col, "ICON STYLE");
        HorizontalScrollView sh = new HorizontalScrollView(c);
        sh.setHorizontalScrollBarEnabled(false);
        LinearLayout srow = new LinearLayout(c);
        for (int i = 0; i < IconStyler.NAMES.length; i++) {
            final int style = i;
            LinearLayout cell = new LinearLayout(c);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(dp(8), dp(8), dp(8), dp(8));
            ImageView iv = new ImageView(c);
            iv.setImageBitmap(host.sample(i));
            cell.addView(iv, new LinearLayout.LayoutParams(dp(60), dp(60)));
            TextView t = text(IconStyler.NAMES[i], 12, Theme.text, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(4), 0, 0);
            cell.addView(t);
            cell.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { host.setIconStyle(style); refreshStyles(); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(78), -2);
            lp.rightMargin = dp(8);
            srow.addView(cell, lp);
            styleCells[i] = cell;
        }
        sh.addView(srow);
        st.addView(sh, new LinearLayout.LayoutParams(-1, -2));
        refreshStyles();

        // ---- 3. icon size
        LinearLayout sz = card(col, "ICON SIZE");
        final TextView sizeLabel = text("Size  " + host.iconSize() + "%", 14, Theme.text, false);
        sz.addView(sizeLabel, new LinearLayout.LayoutParams(-1, -2));
        SeekBar size = new SeekBar(c);
        size.setMax(SIZE_MAX - SIZE_MIN);
        size.setProgress(host.iconSize() - SIZE_MIN);
        tint(size);
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) { sizeLabel.setText("Size  " + (p + SIZE_MIN) + "%"); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) { host.setIconSize(s.getProgress() + SIZE_MIN); }
        });
        sz.addView(size, new LinearLayout.LayoutParams(-1, -2));

        // ---- 4. icon labels
        LinearLayout lb = card(col, "ICON LABELS");
        toggleRow(lb, "Show app names on home", "labels_show", true);
        seekRow(lb, "Text size", "labels_size", 9, 15, 11);

        // ---- 5. home layout
        LinearLayout lay = card(col, "HOME SCREEN LAYOUT");
        seekRow(lay, "Columns", "grid_cols", 3, 6, 4);
        seekRow(lay, "Rows", "grid_rows", 4, 7, 5);
        note(lay, "Icons reflow on their own. Big icons with many rows may overlap.");

        // ---- 6. app drawer
        LinearLayout dr = card(col, "APP DRAWER");
        toggleRow(dr, "Enable app drawer", "drawer_on", true);
        note(dr, "Off: every app sits on the home screen and swipe up opens search.");
        seekRow(dr, "Columns", "drawer_cols", 3, 6, 4);
        chipsRow(dr, new String[]{"Vertical scroll", "Vertical with fast scroller", "Horizontal pages"}, "drawer_scroll", 0);

        // ---- 7. app search
        LinearLayout se = card(col, "APP SEARCH");
        toggleRow(se, "Search phone settings", "search_settings", true);
        toggleRow(se, "Search contacts", "search_contacts", false);
        note(se, "Apps are always searched. Swipe up and type.");

        // ---- 8. widgets
        LinearLayout wg = card(col, "WIDGETS");
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-1, -2);
        wlp.bottomMargin = dp(6);
        wg.addView(pill("Add widget", new Runnable() { public void run() { host.addWidget(); } }), wlp);
        toggleRow(wg, "Show \u22EE menu button on widgets", "widgets_menu", true);
        toggleRow(wg, "Lock widgets", "widgets_lock", false);
        seekRow(wg, "Corner radius", "widgets_radius", 0, 32, 0);
        seekRow(wg, "Spacing", "widgets_gap", 0, 12, 4);
        note(wg, "Resize: tap \u22EE, then Resize. Arrange: long-press a widget and drag it (drop on Remove to delete). Lock stops accidental moves.");

        // ---- 9. dock
        LinearLayout dk = card(col, "DOCK");
        toggleRow(dk, "Show dock", "dock_show", true);
        seekRow(dk, "Icons in dock", "dock_count", 3, 6, 4);
        seekRow(dk, "Background opacity", "dock_alpha", 0, 100, 27);
        seekRow(dk, "Corner radius", "dock_radius", 0, 40, 30);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, -2);
        dlp.topMargin = dp(12);
        dk.addView(pill("Choose dock apps", new Runnable() { public void run() { host.chooseDock(); } }), dlp);
        note(dk, "You can also long-press a dock icon to replace or remove it, or long-press any app and pick Add to dock.");

        // ---- 10. gestures
        LinearLayout gs = card(col, "GESTURES");
        actionRow(gs, "Swipe up", "gest_up", 1);
        actionRow(gs, "Swipe down", "gest_down", 3);
        actionRow(gs, "Double-tap empty space", "gest_double", 0);
        actionRow(gs, "Pinch in", "gest_pinch_in", 0);
        actionRow(gs, "Pinch out", "gest_pinch_out", 0);
        seekRow(gs, "Swipe sensitivity (1 firm, 5 light)", "gest_sens", 1, 5, 3);
        note(gs, "Long-press empty space always opens the home menu. With the drawer open, swiping down closes it.");

        // ---- 11. notification badges
        LinearLayout bd = card(col, "NOTIFICATION BADGES");
        chipsRow(bd, new String[]{"Dots", "Numbers", "Hidden"}, "badge_style", 0);
        toggleRow(bd, "Show on dock icons", "badge_dock", true);
        toggleRow(bd, "Show on folders", "badge_folders", true);
        LinearLayout.LayoutParams blp2 = new LinearLayout.LayoutParams(-1, -2);
        blp2.topMargin = dp(12);
        bd.addView(pill("Allow notification access", new Runnable() { public void run() { host.openBadgeAccess(); } }), blp2);
        badgeStatus = text("", 13, 0xFFFFB27A, true);
        badgeStatus.setPadding(0, dp(8), 0, 0);
        bd.addView(badgeStatus, new LinearLayout.LayoutParams(-1, -2));
        refreshBadgeStatus();
        note(bd, "Badges need notification access. On Android 13+, if the switch is greyed out: Settings > Apps > Voltage > three dots > Allow restricted settings.");

        // ---- 12. animation effects
        LinearLayout an = card(col, "ANIMATION EFFECTS");
        segRow(an, "App opening", new String[]{"Zoom", "Fade", "Slide up", "None"}, "anim_open", 0);
        segRow(an, "App closing (coming back home)", new String[]{"Zoom", "Fade", "Slide down", "None"}, "anim_close", 0);
        segRow(an, "Page scrolling", new String[]{"Slide", "Cube", "Zoom", "Fade"}, "anim_scroll", 0);
        segRow(an, "Folder opening", new String[]{"Pop", "Fade", "Slide up", "None"}, "anim_folder", 0);
        seekRow(an, "Animation speed %", "anim_speed", 50, 200, 100);

        // ---- 13. folder style
        LinearLayout fs = card(col, "FOLDER STYLE");
        segRow(fs, "Style", new String[]{"Glass", "Transparent", "3D", "Rounded"}, "folder_style", 0);
        seekRow(fs, "Folder size %", "folder_size", 70, 130, 100);

        // ---- 14. theme
        LinearLayout th = card(col, "THEME");
        segRow(th, "Mode", new String[]{"Dark", "Light", "Auto (system)", "Custom"}, "theme_mode", 0);
        swatchRow(th, "Custom accent colour", "theme_accent", Theme.ACCENTS, Theme.ACCENTS[0]);
        swatchRow(th, "Custom background", "theme_bg", Theme.BACKGROUNDS, Theme.BACKGROUNDS[1]);
        note(th, "Applies to settings, the app drawer and folder windows. Home screen names stay white so they read on any wallpaper.");

        // ---- 15. 3D effects
        LinearLayout fx = card(col, "3D EFFECTS");
        toggleRow(fx, "Icon shadows", "fx_shadows", false);
        seekRow(fx, "Depth (shadow height, parallax, page tilt)", "fx_depth", 0, 100, 50);
        toggleRow(fx, "Tilt parallax (icons shift as you tilt the phone)", "fx_parallax", false);
        toggleRow(fx, "Wallpaper moves with pages", "fx_wpscroll", false);
        toggleRow(fx, "Floating icons", "fx_float", false);
        note(fx, "Tilt parallax and floating icons use a little battery. Battery saver turns them off.");

        // ---- 16. screen lock
        LinearLayout lk = card(col, "SCREEN LOCK");
        toggleRow(lk, "Enable number lock", "lock_on", false);
        toggleRow(lk, "Lock when the screen turns off", "lock_screenoff", true);
        seekRow(lk, "How many numbers", "lock_count", 4, 9, 6);
        seekRow(lk, "Floating speed", "lock_speed", 1, 5, 3);
        pillIn(lk, "Preview", new Runnable() { public void run() { host.lockPreview(); } });
        pillIn(lk, "Lock now", new Runnable() { public void run() { host.lockNow(); } });
        note(lk, "Glossy 3D numbers float around. Tap them from the smallest to the biggest; the biggest one unlocks. A wrong tap starts over. This lock covers Voltage only, not other apps or the phone's own lock screen, so keep your normal screen lock on.");

        // ---- 17. hide apps
        LinearLayout hd = card(col, "HIDE APPS");
        pillIn(hd, "Choose apps to hide", new Runnable() { public void run() { host.chooseHidden(); } });
        toggleRow(hd, "Also hide from search", "hide_search", true);
        note(hd, "Hidden apps disappear from the app drawer (and from home when the drawer is off). Open this list again and untick to bring them back. They still run normally.");

        // ---- 18. app lock
        LinearLayout al = card(col, "APP LOCK");
        toggleRow(al, "Protect selected apps", "applock_on", false);
        pillIn(al, "Choose protected apps", new Runnable() { public void run() { host.chooseProtected(); } });
        segRow(al, "Ask again", new String[]{"Every time", "After 1 min", "After 5 min"}, "applock_delay", 0);
        note(al, "Opening a protected app from Voltage (home, dock, drawer, folders, search) first asks for the number puzzle. It cannot stop the app being opened from notifications, recent apps or another launcher.");

        // ---- 19. backup and restore
        LinearLayout bk = card(col, "BACKUP & RESTORE");
        pillIn(bk, "Save backup to a file", new Runnable() { public void run() { host.backup(); } });
        pillIn(bk, "Restore from a file", new Runnable() { public void run() { host.restore(); } });
        pillIn(bk, "Reset Voltage to defaults", new Runnable() { public void run() { host.resetAll(); } });
        note(bk, "The backup holds your home layout, folders, dock, icon style and size, and every setting on this screen. Wallpaper pictures are not included. Widgets come back only if they still exist on the phone.");

        // ---- 20. performance and battery
        LinearLayout pf = card(col, "PERFORMANCE & BATTERY");
        segRow(pf, "Mode", new String[]{"Balanced", "Battery saver", "Smooth"}, "perf_mode", 0);
        toggleRow(pf, "Reduce animations", "reduce_anim", false);
        toggleRow(pf, "Auto battery saver (battery 20% or lower, or phone's power saver)", "auto_saver", true);
        toggleRow(pf, "Free memory when Voltage is in the background", "trim_mem", true);
        note(pf, "Battery saver switches off page effects, floating icons, tilt parallax and most animations. Smooth ignores the auto saver and keeps every effect on.");
    }

    /** Rebuilds the whole screen (used after the theme changes). */
    void rebuild() {
        final int y = scroll != null ? scroll.getScrollY() : 0;
        removeAllViews();
        syncers.clear();
        for (int i = 0; i < styleCells.length; i++) styleCells[i] = null;
        setBackgroundColor(Theme.bg);
        build();
        scroll.post(new Runnable() { public void run() { scroll.scrollTo(0, y); } });
    }

    void refreshStyles() {
        int cur = host.iconStyle();
        for (int i = 0; i < styleCells.length; i++) {
            if (styleCells[i] == null) continue;
            GradientDrawable g = box(i == cur ? Theme.accentA(0x33) : Theme.a(0x14), 16);
            if (i == cur) g.setStroke(dp(2), Theme.accent);
            styleCells[i].setBackground(g);
        }
    }

    void scrollToTop() { if (scroll != null) scroll.scrollTo(0, 0); }
}
