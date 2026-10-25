package com.camon18i.voltage;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Full FEG-PLAY screen: Now Playing, Songs, Albums, Artists, Favourites and Queue,
 * with search, a mini player under the lists, sleep timer and volume.
 */
public class FegPlayerScreen implements FegEngine.Listener {
    static final int TAB_NOW = 0, TAB_SONGS = 1, TAB_ALBUMS = 2, TAB_ARTISTS = 3, TAB_FAVS = 4, TAB_QUEUE = 5;
    static final int REQ_AUDIO = 78;
    private static final String[] TAB_NAMES = {"Playing", "Songs", "Albums", "Artists", "Favourites", "Queue"};

    private static FegPlayerScreen current;

    static void show(Activity a, int tab) {
        if (current != null) { current.setTab(tab); return; }
        FegPlayerScreen s = new FegPlayerScreen(a, tab);
        current = s;
        s.dlg.show();
    }

    static void dismissCurrent() {
        if (current != null) current.dlg.dismiss();
    }

    /** Ask for the music permission (and the notification permission on Android 13+). */
    static void requestAccess(Activity a) {
        SharedPreferences sp = a.getSharedPreferences("feg_play", Context.MODE_PRIVATE);
        String perm = FegEngine.audioPermission();
        boolean asked = sp.getBoolean("asked", false);
        if (asked && !a.shouldShowRequestPermissionRationale(perm)) {
            // switched off for good: only the phone's settings can turn it on again
            Toast.makeText(a, "Turn on Music and audio (or Storage) for FEG Launcher, then come back", Toast.LENGTH_LONG).show();
            try {
                a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.getPackageName())));
            } catch (Exception ignored) {}
            return;
        }
        sp.edit().putBoolean("asked", true).apply();
        List<String> need = new ArrayList<>();
        need.add(perm);
        if (Build.VERSION.SDK_INT >= 33) need.add("android.permission.POST_NOTIFICATIONS");
        a.requestPermissions(need.toArray(new String[0]), REQ_AUDIO);
    }

    // ================================================================ rows

    private static class Row {
        boolean group;
        FegEngine.Track track;   // the song, or the first song of a group (for its picture)
        String title = "", sub = "", key;
        int queueIdx = -1;
        int songIdx = -1;        // index in songsShown
    }

    private final Activity act;
    private final FegEngine eng;
    private final Dialog dlg;
    private final AudioManager am;

    private int tab = TAB_NOW;
    private String group;        // album or artist name being browsed
    private String query = "";
    private final List<Row> rows = new ArrayList<>();
    private final List<FegEngine.Track> songsShown = new ArrayList<>();
    private int builtVersion = -1;

    private TextView headTitle, emptyMsg, emptyBtn, playAll, shuffleAll;
    private FegUi.Icon backIc, timerIc, refreshIc;
    private final TextView[] tabViews = new TextView[TAB_NAMES.length];
    private View nowPage, listPage, mini, actionRow;
    private EditText searchBox;
    private ListView listView;
    private RowAdapter adapter;

    // now playing
    private ImageView npArt, miniArt;
    private TextView npTitle, npArtist, npTimeL, npTimeR, npSleep, miniTitle, miniArtist;
    private FegUi.Seek npSeek, npVol;
    private FegUi.Icon npShuf, npPrev, npPlay, npNext, npRep, npFav, miniPlay, miniNext;
    private Object npArtTag, miniArtTag;

    private final Runnable tick = new Runnable() {
        public void run() {
            updateProgress();
            listView.postDelayed(this, 500);
        }
    };

    private FegPlayerScreen(Activity a, int startTab) {
        act = a;
        eng = FegEngine.get(a);
        am = (AudioManager) a.getSystemService(Context.AUDIO_SERVICE);
        dlg = new Dialog(a, android.R.style.Theme_Black_NoTitleBar) {
            @Override public void onBackPressed() { handleBack(); }
        };
        dlg.setVolumeControlStream(AudioManager.STREAM_MUSIC);
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new ColorDrawable(FegUi.BG));
            dlg.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            dlg.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dlg.setContentView(buildUi(), new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface d) {
                eng.removeListener(FegPlayerScreen.this);
                listView.removeCallbacks(tick);
                if (current == FegPlayerScreen.this) current = null;
            }
        });
        eng.addListener(this);
        if (eng.hasPermission()) eng.loadLibrary(false);
        tab = -1;
        setTab(startTab);
        listView.post(tick);
    }

    private int dp(float v) { return FegUi.dp(act, v); }

    private TextView tv(float sp, int color, boolean bold) {
        TextView t = new TextView(act);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private interface Go { void go(); }

    private static View.OnClickListener click(final Go g) {
        return new View.OnClickListener() { @Override public void onClick(View v) { g.go(); } };
    }

    private FegUi.Icon icon(int type, int color, float scale, Go g) {
        FegUi.Icon i = new FegUi.Icon(act, type, color, scale);
        if (g != null) i.setOnClickListener(click(g));
        return i;
    }

    // ================================================================ build UI

    private View buildUi() {
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(FegUi.BG);

        // header
        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(6), dp(4), dp(6), dp(4));
        backIc = icon(FegUi.BACK, Color.WHITE, 0.55f, new Go() { public void go() { handleBack(); } });
        head.addView(backIc, new LinearLayout.LayoutParams(dp(44), dp(48)));
        headTitle = tv(19, Color.WHITE, true);
        headTitle.setLetterSpacing(0.08f);
        head.addView(headTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        timerIc = icon(FegUi.TIMER, FegUi.OFF, 0.55f, new Go() { public void go() { sleepDialog(); } });
        refreshIc = icon(FegUi.REFRESH, FegUi.OFF, 0.55f, new Go() {
            public void go() {
                if (!eng.hasPermission()) requestAccess(act);
                else { eng.loadLibrary(true); Toast.makeText(act, "Looking for music...", Toast.LENGTH_SHORT).show(); }
            }
        });
        head.addView(timerIc, new LinearLayout.LayoutParams(dp(44), dp(48)));
        head.addView(refreshIc, new LinearLayout.LayoutParams(dp(44), dp(48)));
        root.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // tabs
        HorizontalScrollView hs = new HorizontalScrollView(act);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout tabRow = new LinearLayout(act);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setPadding(dp(10), 0, dp(10), dp(6));
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int ti = i;
            TextView t = tv(14, FegUi.SUB, true);
            t.setText(TAB_NAMES[i]);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(14), dp(8), dp(14), dp(8));
            t.setOnClickListener(click(new Go() { public void go() { setTab(ti); } }));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(4);
            tabRow.addView(t, lp);
            tabViews[i] = t;
        }
        hs.addView(tabRow);
        root.addView(hs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // content: now playing page and list page share the space
        FrameLayout content = new FrameLayout(act);
        nowPage = buildNow();
        listPage = buildList();
        content.addView(nowPage, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        content.addView(listPage, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        mini = buildMini();
        root.addView(mini, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));
        return root;
    }

    private View buildNow() {
        ScrollView sv = new ScrollView(act);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(22), dp(8), dp(22), dp(16));
        sv.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int sw = act.getResources().getDisplayMetrics().widthPixels, sh = act.getResources().getDisplayMetrics().heightPixels;
        int artSize = Math.min(sw - dp(70), (int) (sh * 0.38f));
        npArt = new ImageView(act);
        npArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        FegUi.clipRound(npArt, dp(26));
        npArt.setElevation(dp(10));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(artSize, artSize);
        alp.topMargin = dp(8);
        col.addView(npArt, alp);

        LinearLayout tr = new LinearLayout(act);
        tr.setOrientation(LinearLayout.HORIZONTAL);
        tr.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout names = new LinearLayout(act);
        names.setOrientation(LinearLayout.VERTICAL);
        npTitle = tv(21, Color.WHITE, true);
        npArtist = tv(14, FegUi.SUB, false);
        names.addView(npTitle);
        names.addView(npArtist);
        tr.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        npFav = icon(FegUi.HEART, FegUi.OFF, 0.6f, new Go() { public void go() { eng.toggleFav(eng.current()); } });
        tr.addView(npFav, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(22);
        col.addView(tr, tlp);

        LinearLayout sr = new LinearLayout(act);
        sr.setOrientation(LinearLayout.HORIZONTAL);
        sr.setGravity(Gravity.CENTER_VERTICAL);
        npTimeL = tv(12, FegUi.SUB, false);
        npTimeR = tv(12, FegUi.SUB, false);
        npTimeL.setText("0:00");
        npTimeR.setText("0:00");
        npSeek = new FegUi.Seek(act, true);
        npSeek.setListener(new FegUi.SeekListener() {
            public void onScrub(float f) { npTimeL.setText(FegUi.fmt((long) (f * eng.duration()))); }
            public void onCommit(float f) { eng.seekTo((int) (f * eng.duration())); updateProgress(); }
        });
        sr.addView(npTimeL);
        sr.addView(npSeek, new LinearLayout.LayoutParams(0, dp(34), 1f));
        sr.addView(npTimeR);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34));
        slp.topMargin = dp(10);
        col.addView(sr, slp);

        LinearLayout ctl = new LinearLayout(act);
        ctl.setOrientation(LinearLayout.HORIZONTAL);
        ctl.setGravity(Gravity.CENTER);
        npShuf = icon(FegUi.SHUFFLE, FegUi.OFF, 0.5f, new Go() { public void go() { eng.toggleShuffle(); } });
        npPrev = icon(FegUi.PREV, Color.WHITE, 0.62f, new Go() { public void go() { eng.prev(); } });
        npPlay = icon(FegUi.PLAY, Color.WHITE, 0.55f, new Go() { public void go() { playPressed(); } });
        npPlay.setBackground(FegUi.round(FegUi.ACCENT, dp(40)));
        npNext = icon(FegUi.NEXT, Color.WHITE, 0.62f, new Go() { public void go() { eng.next(); } });
        npRep = icon(FegUi.REPEAT, FegUi.OFF, 0.5f, new Go() { public void go() { eng.cycleRepeat(); } });
        ctl.addView(npShuf, new LinearLayout.LayoutParams(0, dp(52), 1f));
        ctl.addView(npPrev, new LinearLayout.LayoutParams(0, dp(52), 1f));
        ctl.addView(npPlay, new LinearLayout.LayoutParams(dp(68), dp(68)));
        ctl.addView(npNext, new LinearLayout.LayoutParams(0, dp(52), 1f));
        ctl.addView(npRep, new LinearLayout.LayoutParams(0, dp(52), 1f));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72));
        clp.topMargin = dp(8);
        col.addView(ctl, clp);

        LinearLayout vr = new LinearLayout(act);
        vr.setOrientation(LinearLayout.HORIZONTAL);
        vr.setGravity(Gravity.CENTER_VERTICAL);
        vr.addView(icon(FegUi.VOLUME, FegUi.SUB, 0.6f, null), new LinearLayout.LayoutParams(dp(32), dp(34)));
        npVol = new FegUi.Seek(act, true);
        npVol.setColors(0xFFBBBBBB, 0x33FFFFFF);
        npVol.setListener(new FegUi.SeekListener() {
            public void onScrub(float f) { setVolume(f); }
            public void onCommit(float f) { setVolume(f); }
        });
        vr.addView(npVol, new LinearLayout.LayoutParams(0, dp(34), 1f));
        npSleep = tv(12.5f, FegUi.SUB, true);
        npSleep.setGravity(Gravity.CENTER);
        npSleep.setPadding(dp(12), dp(7), dp(12), dp(7));
        npSleep.setBackground(FegUi.round(FegUi.CARD2, dp(20)));
        npSleep.setOnClickListener(click(new Go() { public void go() { sleepDialog(); } }));
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nl.leftMargin = dp(12);
        vr.addView(npSleep, nl);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        vlp.topMargin = dp(10);
        col.addView(vr, vlp);
        return sv;
    }

    private View buildList() {
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);

        searchBox = new EditText(act);
        searchBox.setHint("Search");
        searchBox.setHintTextColor(FegUi.SUB);
        searchBox.setTextColor(Color.WHITE);
        searchBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        searchBox.setSingleLine(true);
        searchBox.setBackground(FegUi.round(FegUi.CARD2, dp(22)));
        searchBox.setPadding(dp(16), 0, dp(16), 0);
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable e) {
                String q = e.toString().trim();
                if (q.equals(query)) return;
                query = q;
                rebuild();
            }
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        sl.setMargins(dp(14), dp(2), dp(14), dp(8));
        col.addView(searchBox, sl);

        LinearLayout ar = new LinearLayout(act);
        ar.setOrientation(LinearLayout.HORIZONTAL);
        playAll = tv(14, Color.WHITE, true);
        playAll.setText("Play all");
        playAll.setGravity(Gravity.CENTER);
        playAll.setBackground(FegUi.round(FegUi.ACCENT, dp(22)));
        playAll.setOnClickListener(click(new Go() { public void go() { startShown(false); } }));
        shuffleAll = tv(14, Color.WHITE, true);
        shuffleAll.setText("Shuffle");
        shuffleAll.setGravity(Gravity.CENTER);
        shuffleAll.setBackground(FegUi.round(FegUi.CARD2, dp(22)));
        shuffleAll.setOnClickListener(click(new Go() { public void go() { startShown(true); } }));
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, dp(40), 1f);
        p1.rightMargin = dp(8);
        ar.addView(playAll, p1);
        ar.addView(shuffleAll, new LinearLayout.LayoutParams(0, dp(40), 1f));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.setMargins(dp(14), 0, dp(14), dp(6));
        col.addView(ar, alp);
        actionRow = ar;

        FrameLayout box = new FrameLayout(act);
        listView = new ListView(act);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setSelector(new ColorDrawable(0x22FFFFFF));
        listView.setVerticalScrollBarEnabled(false);
        adapter = new RowAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int i, long id) { rowClicked(rows.get(i)); }
        });
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> p, View v, int i, long id) {
                Row r = rows.get(i);
                if (r.group) return false;
                songMenu(r);
                return true;
            }
        });
        box.addView(listView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout empty = new LinearLayout(act);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(30), 0, dp(30), dp(40));
        emptyMsg = tv(15, FegUi.SUB, false);
        emptyMsg.setSingleLine(false);
        emptyMsg.setGravity(Gravity.CENTER);
        emptyBtn = tv(15, Color.WHITE, true);
        emptyBtn.setGravity(Gravity.CENTER);
        emptyBtn.setPadding(dp(26), dp(12), dp(26), dp(12));
        emptyBtn.setBackground(FegUi.round(FegUi.ACCENT, dp(24)));
        empty.addView(emptyMsg);
        LinearLayout.LayoutParams ebl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ebl.topMargin = dp(16);
        empty.addView(emptyBtn, ebl);
        empty.setTag("empty");
        box.addView(empty, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        listView.setEmptyView(empty);
        col.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return col;
    }

    private View buildMini() {
        LinearLayout m = new LinearLayout(act);
        m.setOrientation(LinearLayout.HORIZONTAL);
        m.setGravity(Gravity.CENTER_VERTICAL);
        m.setBackgroundColor(FegUi.CARD);
        m.setPadding(dp(10), dp(6), dp(6), dp(6));
        m.setOnClickListener(click(new Go() { public void go() { setTab(TAB_NOW); } }));
        miniArt = new ImageView(act);
        miniArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        FegUi.clipRound(miniArt, dp(8));
        m.addView(miniArt, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout t = new LinearLayout(act);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(10), 0, dp(6), 0);
        miniTitle = tv(14, Color.WHITE, true);
        miniArtist = tv(12, FegUi.SUB, false);
        t.addView(miniTitle);
        t.addView(miniArtist);
        m.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        miniPlay = icon(FegUi.PLAY, Color.WHITE, 0.6f, new Go() { public void go() { playPressed(); } });
        miniNext = icon(FegUi.NEXT, Color.WHITE, 0.55f, new Go() { public void go() { eng.next(); } });
        m.addView(miniPlay, new LinearLayout.LayoutParams(dp(46), dp(46)));
        m.addView(miniNext, new LinearLayout.LayoutParams(dp(46), dp(46)));
        return m;
    }

    // ================================================================ actions

    private void playPressed() {
        if (!eng.hasPermission()) { requestAccess(act); return; }
        eng.togglePlay();
    }

    private void handleBack() {
        if (tab != TAB_NOW && group != null) {
            group = null;
            setQuery("");
            rebuild();
            updateChrome();
            return;
        }
        dlg.dismiss();
    }

    private void setQuery(String q) {
        query = q;
        if (!searchBox.getText().toString().equals(q)) searchBox.setText(q);
    }

    private void setTab(int t) {
        if (t == tab && group == null) return;
        boolean switchedList = t != tab;
        tab = t;
        if (switchedList) {
            group = null;
            setQuery("");
        }
        rebuild();
        updateChrome();
        refreshNow();
    }

    /** show / hide the right pieces for the current tab */
    private void updateChrome() {
        boolean now = tab == TAB_NOW;
        nowPage.setVisibility(now ? View.VISIBLE : View.GONE);
        listPage.setVisibility(now ? View.GONE : View.VISIBLE);
        boolean searchable = tab == TAB_SONGS || tab == TAB_ALBUMS || tab == TAB_ARTISTS || tab == TAB_FAVS;
        searchBox.setVisibility(searchable ? View.VISIBLE : View.GONE);
        searchBox.setHint(tab == TAB_ALBUMS && group == null ? "Search albums" : tab == TAB_ARTISTS && group == null ? "Search artists" : "Search songs");
        boolean songList = tab == TAB_SONGS || tab == TAB_FAVS || (group != null && (tab == TAB_ALBUMS || tab == TAB_ARTISTS));
        actionRow.setVisibility(songList && !songsShown.isEmpty() ? View.VISIBLE : View.GONE);
        headTitle.setText(group != null && !now ? group : "FEG-PLAY");
        for (int i = 0; i < tabViews.length; i++) {
            boolean on = i == tab;
            tabViews[i].setTextColor(on ? Color.WHITE : FegUi.SUB);
            tabViews[i].setBackground(on ? FegUi.round(FegUi.ACCENT, dp(18)) : null);
        }
        updateMini();
        updateEmpty();
    }

    private void updateMini() {
        boolean show = tab != TAB_NOW && eng.current() != null;
        mini.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;
        FegEngine.Track t = eng.current();
        miniTitle.setText(t.title);
        miniArtist.setText(t.artist);
        miniPlay.set(eng.isPlaying() ? FegUi.PAUSE : FegUi.PLAY, Color.WHITE);
        Object tag = Long.valueOf(t.id);
        if (!tag.equals(miniArtTag)) {
            miniArtTag = tag;
            miniArt.setImageDrawable(new FegUi.ArtPlaceholder(t.album));
            loadInto(miniArt, t, 128, tag, true);
        }
    }

    private void updateEmpty() {
        String msg;
        String btn = null;
        Go act1 = null;
        if (!eng.hasPermission()) {
            msg = "FEG-PLAY needs permission to read the music on your phone.";
            btn = "Allow access";
            act1 = new Go() { public void go() { requestAccess(act); } };
        } else if (eng.loading && eng.library.isEmpty()) {
            msg = "Looking for music...";
        } else if (eng.library.isEmpty()) {
            msg = "No music found on this phone.";
            btn = "Scan again";
            act1 = new Go() { public void go() { eng.loadLibrary(true); } };
        } else if (tab == TAB_FAVS && query.length() == 0) {
            msg = "No favourites yet.\nTap the heart on the Playing screen, or long-press a song.";
        } else if (tab == TAB_QUEUE) {
            msg = "The queue is empty.\nPlay a song to fill it.";
        } else {
            msg = "Nothing matches your search.";
        }
        emptyMsg.setText(msg);
        emptyBtn.setVisibility(btn == null ? View.GONE : View.VISIBLE);
        if (btn != null) { emptyBtn.setText(btn); emptyBtn.setOnClickListener(click(act1)); }
    }

    private void startShown(boolean shuffleOn) {
        if (songsShown.isEmpty()) return;
        if (eng.shuffle != shuffleOn) eng.setShuffle(shuffleOn);
        int start = shuffleOn ? (int) (Math.random() * songsShown.size()) : 0;
        eng.playList(new ArrayList<>(songsShown), start);
    }

    private void rowClicked(Row r) {
        if (r.group) {
            group = r.key;
            setQuery("");
            rebuild();
            updateChrome();
            listView.setSelection(0);
            return;
        }
        if (tab == TAB_QUEUE) { eng.jumpTo(r.queueIdx); return; }
        eng.playList(new ArrayList<>(songsShown), r.songIdx);
    }

    private void songMenu(final Row r) {
        final FegEngine.Track t = r.track;
        final boolean inQueue = tab == TAB_QUEUE;
        final List<String> items = new ArrayList<>();
        items.add("Play next");
        items.add("Add to queue");
        items.add(eng.isFav(t) ? "Remove from favourites" : "Add to favourites");
        if (inQueue) items.add("Remove from queue");
        new AlertDialog.Builder(act).setTitle(t.title)
                .setItems(items.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        switch (which) {
                            case 0: eng.playNext(t); Toast.makeText(act, "Plays next", Toast.LENGTH_SHORT).show(); break;
                            case 1: eng.addToQueue(t); Toast.makeText(act, "Added to queue", Toast.LENGTH_SHORT).show(); break;
                            case 2: eng.toggleFav(t); break;
                            default:
                                if (!eng.removeFromQueue(r.queueIdx))
                                    Toast.makeText(act, "This song is playing now", Toast.LENGTH_SHORT).show();
                                break;
                        }
                    }
                }).show();
    }

    private void sleepDialog() {
        final int[] mins = {0, 15, 30, 45, 60, 90};
        String[] names = {"Off", "15 minutes", "30 minutes", "45 minutes", "1 hour", "1 hour 30 minutes"};
        int left = eng.sleepLeftMin();
        new AlertDialog.Builder(act)
                .setTitle(left > 0 ? "Sleep timer (" + left + " min left)" : "Sleep timer")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        eng.setSleep(mins[which]);
                        Toast.makeText(act, mins[which] == 0 ? "Sleep timer off" : "Music stops in " + mins[which] + " min", Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    private void setVolume(float f) {
        if (am == null) return;
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        try { am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(f * max), 0); } catch (Exception ignored) {}
    }

    // ================================================================ data

    private boolean matches(String q, String... fields) {
        if (q.length() == 0) return true;
        for (String f : fields) if (f != null && f.toLowerCase().contains(q)) return true;
        return false;
    }

    private void rebuild() {
        builtVersion = eng.version;
        rows.clear();
        songsShown.clear();
        String q = query.toLowerCase();
        boolean groupTab = tab == TAB_ALBUMS || tab == TAB_ARTISTS;
        if (tab == TAB_SONGS || tab == TAB_FAVS || (groupTab && group != null)) {
            for (FegEngine.Track t : eng.library) {
                if (tab == TAB_FAVS && !eng.isFav(t)) continue;
                if (groupTab && !(tab == TAB_ALBUMS ? t.album : t.artist).equals(group)) continue;
                if (!matches(q, t.title, t.artist, t.album)) continue;
                Row r = new Row();
                r.track = t;
                r.title = t.title;
                r.sub = t.artist + " · " + FegUi.fmt(t.duration);
                r.songIdx = songsShown.size();
                songsShown.add(t);
                rows.add(r);
            }
        } else if (groupTab) {
            Map<String, List<FegEngine.Track>> g = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (FegEngine.Track t : eng.library) {
                String k = tab == TAB_ALBUMS ? t.album : t.artist;
                List<FegEngine.Track> l = g.get(k);
                if (l == null) { l = new ArrayList<>(); g.put(k, l); }
                l.add(t);
            }
            for (Map.Entry<String, List<FegEngine.Track>> e : g.entrySet()) {
                FegEngine.Track first = e.getValue().get(0);
                if (!matches(q, e.getKey(), tab == TAB_ALBUMS ? first.artist : null)) continue;
                Row r = new Row();
                r.group = true;
                r.key = e.getKey();
                r.track = first;
                r.title = e.getKey();
                int n = e.getValue().size();
                r.sub = (tab == TAB_ALBUMS ? first.artist + " · " : "") + n + (n == 1 ? " song" : " songs");
                rows.add(r);
            }
        } else if (tab == TAB_QUEUE) {
            for (int i = 0; i < eng.queue.size(); i++) {
                FegEngine.Track t = eng.queue.get(i);
                Row r = new Row();
                r.track = t;
                r.title = t.title;
                r.sub = t.artist + " · " + FegUi.fmt(t.duration);
                r.queueIdx = i;
                rows.add(r);
            }
        }
        adapter.notifyDataSetChanged();
        boolean songList = tab == TAB_SONGS || tab == TAB_FAVS || (group != null && groupTab);
        if (actionRow != null) actionRow.setVisibility(songList && !songsShown.isEmpty() ? View.VISIBLE : View.GONE);
        if (tab == TAB_QUEUE && eng.pos > 2) listView.post(new Runnable() { public void run() { listView.setSelection(Math.max(0, eng.pos - 1)); } });
    }

    @Override public void onChange() {
        if (builtVersion != eng.version) rebuild(); else adapter.notifyDataSetChanged();
        updateChrome();
        refreshNow();
    }

    // ================================================================ now playing page

    private void refreshNow() {
        FegEngine.Track t = eng.current();
        if (t == null) {
            npTitle.setText(eng.hasPermission() ? "Nothing playing" : "Allow music access");
            npArtist.setText(eng.hasPermission() ? "Pick a song from Songs" : "Tap play to start");
        } else {
            npTitle.setText(t.title);
            npArtist.setText(t.artist + (t.album.length() > 0 ? " · " + t.album : ""));
        }
        npPlay.set(eng.isPlaying() ? FegUi.PAUSE : FegUi.PLAY, Color.WHITE);
        npShuf.set(FegUi.SHUFFLE, eng.shuffle ? FegUi.ACCENT : FegUi.OFF);
        npRep.set(eng.repeat == FegEngine.REPEAT_ONE ? FegUi.REPEAT1 : FegUi.REPEAT, eng.repeat == FegEngine.REPEAT_OFF ? FegUi.OFF : FegUi.ACCENT);
        boolean fav = eng.isFav(t);
        npFav.set(fav ? FegUi.HEART_ON : FegUi.HEART, fav ? FegUi.ACCENT : FegUi.OFF);
        timerIc.set(FegUi.TIMER, eng.sleepLeftMin() > 0 ? FegUi.ACCENT : FegUi.OFF);

        Object tag = t == null ? "none" : (Object) Long.valueOf(t.id);
        if (!tag.equals(npArtTag)) {
            npArtTag = tag;
            npArt.setImageDrawable(new FegUi.ArtPlaceholder(t == null ? "FEG-PLAY" : t.album));
            if (t != null) loadInto(npArt, t, 600, tag, false);
        }
        updateProgress();
    }

    private void loadInto(final ImageView iv, FegEngine.Track t, int px, final Object tag, final boolean mini) {
        eng.art(t, px, new FegEngine.ArtCb() {
            public void on(Bitmap b) {
                Object cur = mini ? miniArtTag : npArtTag;
                if (b != null && tag.equals(cur)) iv.setImageBitmap(b);
            }
        });
    }

    private void updateProgress() {
        int d = eng.duration(), p = eng.position();
        if (!npSeek.isDragging()) {
            npSeek.setFrac(d > 0 ? p / (float) d : 0f);
            npTimeL.setText(FegUi.fmt(p));
            npTimeR.setText(FegUi.fmt(d));
        }
        int left = eng.sleepLeftMin();
        npSleep.setText(left > 0 ? "Sleep in " + left + " min" : "Sleep timer");
        npSleep.setTextColor(left > 0 ? FegUi.ACCENT : FegUi.SUB);
        if (!npVol.isDragging() && am != null) {
            int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            npVol.setFrac(am.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) max);
        }
    }

    // ================================================================ list rows

    private class RowAdapter extends BaseAdapter {
        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int i) { return rows.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override public View getView(int i, View v, ViewGroup parent) {
            Holder h;
            if (v == null) {
                LinearLayout row = new LinearLayout(act);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(14), dp(7), dp(6), dp(7));
                h = new Holder();
                h.art = new ImageView(act);
                h.art.setScaleType(ImageView.ScaleType.CENTER_CROP);
                FegUi.clipRound(h.art, dp(10));
                row.addView(h.art, new LinearLayout.LayoutParams(dp(50), dp(50)));
                LinearLayout t = new LinearLayout(act);
                t.setOrientation(LinearLayout.VERTICAL);
                t.setPadding(dp(12), 0, dp(6), 0);
                h.title = tv(15, Color.WHITE, true);
                h.sub = tv(12.5f, FegUi.SUB, false);
                t.addView(h.title);
                t.addView(h.sub);
                row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                h.more = new FegUi.Icon(act, FegUi.MORE, FegUi.SUB, 0.55f);
                row.addView(h.more, new LinearLayout.LayoutParams(dp(40), dp(44)));
                row.setTag(h);
                v = row;
            } else {
                h = (Holder) v.getTag();
            }
            final Row r = rows.get(i);
            boolean cur = !r.group && (tab == TAB_QUEUE ? r.queueIdx == eng.pos : r.track == eng.current());
            h.title.setText(r.title);
            h.title.setTextColor(cur ? FegUi.ACCENT : Color.WHITE);
            h.sub.setText(r.sub);
            v.setBackgroundColor(cur ? 0x1AE53935 : Color.TRANSPARENT);
            h.more.setVisibility(r.group ? View.INVISIBLE : View.VISIBLE);
            h.more.setOnClickListener(r.group ? null : click(new Go() { public void go() { songMenu(r); } }));
            h.more.setClickable(!r.group);
            final Object tag = Long.valueOf(r.track.id);
            h.art.setTag(tag);
            h.art.setImageDrawable(new FegUi.ArtPlaceholder(r.track.album));
            final ImageView iv = h.art;
            eng.art(r.track, 128, new FegEngine.ArtCb() {
                public void on(Bitmap b) { if (b != null && tag.equals(iv.getTag())) iv.setImageBitmap(b); }
            });
            return v;
        }
    }

    private static class Holder {
        ImageView art;
        TextView title, sub;
        FegUi.Icon more;
    }
}
