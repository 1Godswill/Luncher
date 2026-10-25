package com.camon18i.voltage;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The FEG-PLAY music player that sits on the home screen like a widget.
 * One row high = small player (song, previous, play, next). Two rows or more = full widget player
 * with album art, progress bar, shuffle and repeat. Tap the song to open the full FEG-PLAY screen.
 */
public class FegPlayerView extends FrameLayout implements FegEngine.Listener {
    private final Activity act;
    private final FegEngine eng;
    private final boolean compact;
    private final int rows;

    private ImageView art;
    private TextView title, artist, timeL, timeR, brand;
    private FegUi.Icon play, prev, next, shuf, rep, lib;
    private FegUi.Seek seek;
    private Object artTag;

    private final Runnable tick = new Runnable() {
        public void run() {
            updateProgress();
            postDelayed(this, 500);
        }
    };

    public FegPlayerView(Activity a, int rows) {
        super(a);
        act = a;
        eng = FegEngine.get(a);
        this.rows = rows;
        compact = rows < 2;
        build();
    }

    private int dp(float v) { return FegUi.dp(getContext(), v); }

    private void build() {
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xE61F2129, 0xE60E0F13});
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), 0x33FFFFFF);
        setBackground(bg);
        FegUi.clipRound(this, dp(22));
        setPadding(dp(12), dp(10), dp(12), dp(10));

        if (compact) buildCompact(); else buildFull();
    }

    private TextView text(float sp, int color, boolean bold) {
        TextView t = new TextView(getContext());
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private FegUi.Icon btn(int type, float sizeDp, float scale, OnClick c) {
        FegUi.Icon i = new FegUi.Icon(getContext(), type, Color.WHITE, scale);
        i.setOnClickListener(new ClickAdapter(c));
        return i;
    }

    private interface OnClick { void go(); }

    private static class ClickAdapter implements View.OnClickListener {
        private final OnClick c;
        ClickAdapter(OnClick c) { this.c = c; }
        @Override public void onClick(View v) { c.go(); }
    }

    private void openScreen(int tab) { FegPlayerScreen.show(act, tab); }

    private void onPlayClick() {
        if (!eng.hasPermission()) { FegPlayerScreen.requestAccess(act); return; }
        if (!eng.loaded) { eng.loadLibrary(true); return; }
        if (eng.library.isEmpty()) { eng.loadLibrary(true); return; }
        eng.togglePlay();
    }

    // ---------------------------------------------------------------- small player (1 row)

    private void buildCompact() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        addView(row, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        art = new ImageView(getContext());
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        FegUi.clipRound(art, dp(12));
        row.addView(art, new LinearLayout.LayoutParams(dp(50), dp(50)));

        LinearLayout info = new LinearLayout(getContext());
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(10), 0, dp(6), 0);
        title = text(14, Color.WHITE, true);
        artist = text(11.5f, FegUi.SUB, false);
        info.addView(title);
        info.addView(artist);
        info.setOnClickListener(new ClickAdapter(new OnClick() { public void go() { openScreen(FegPlayerScreen.TAB_NOW); } }));
        row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        art.setOnClickListener(new ClickAdapter(new OnClick() { public void go() { openScreen(FegPlayerScreen.TAB_NOW); } }));

        prev = btn(FegUi.PREV, 36, 0.5f, new OnClick() { public void go() { eng.prev(); } });
        play = btn(FegUi.PLAY, 44, 0.62f, new OnClick() { public void go() { onPlayClick(); } });
        next = btn(FegUi.NEXT, 36, 0.5f, new OnClick() { public void go() { eng.next(); } });
        play.setBackground(FegUi.round(FegUi.ACCENT, dp(40)));
        row.addView(prev, new LinearLayout.LayoutParams(dp(38), dp(44)));
        row.addView(play, new LinearLayout.LayoutParams(dp(44), dp(44)));
        row.addView(next, new LinearLayout.LayoutParams(dp(38), dp(44)));

        seek = new FegUi.Seek(getContext(), false);
        seek.setTouchable(false);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3), Gravity.BOTTOM);
        sl.leftMargin = dp(60);
        sl.rightMargin = dp(4);
        sl.bottomMargin = -dp(4);
        addView(seek, sl);
    }

    // ---------------------------------------------------------------- full widget player (2+ rows)

    private void buildFull() {
        LinearLayout col = new LinearLayout(getContext());
        col.setOrientation(LinearLayout.VERTICAL);
        addView(col, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout top = new LinearLayout(getContext());
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        col.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int artSize = rows >= 3 ? dp(96) : dp(64);
        art = new ImageView(getContext());
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        FegUi.clipRound(art, dp(14));
        top.addView(art, new LinearLayout.LayoutParams(artSize, artSize));
        art.setOnClickListener(new ClickAdapter(new OnClick() { public void go() { openScreen(FegPlayerScreen.TAB_NOW); } }));

        LinearLayout info = new LinearLayout(getContext());
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(12), 0, dp(6), 0);
        brand = text(10, FegUi.ACCENT, true);
        brand.setText("FEG-PLAY");
        brand.setLetterSpacing(0.15f);
        title = text(rows >= 3 ? 17 : 15, Color.WHITE, true);
        artist = text(12.5f, FegUi.SUB, false);
        info.addView(brand);
        info.addView(title);
        info.addView(artist);
        info.setOnClickListener(new ClickAdapter(new OnClick() { public void go() { openScreen(FegPlayerScreen.TAB_NOW); } }));
        top.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        lib = btn(FegUi.LIST, 36, 0.58f, new OnClick() { public void go() { openScreen(FegPlayerScreen.TAB_SONGS); } });
        top.addView(lib, new LinearLayout.LayoutParams(dp(40), dp(40)));

        View spacer = new View(getContext());
        col.addView(spacer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout sr = new LinearLayout(getContext());
        sr.setOrientation(LinearLayout.HORIZONTAL);
        sr.setGravity(Gravity.CENTER_VERTICAL);
        timeL = text(10.5f, FegUi.SUB, false);
        timeR = text(10.5f, FegUi.SUB, false);
        timeL.setText("0:00");
        timeR.setText("0:00");
        seek = new FegUi.Seek(getContext(), true);
        seek.setListener(new FegUi.SeekListener() {
            public void onScrub(float f) { timeL.setText(FegUi.fmt((long) (f * eng.duration()))); }
            public void onCommit(float f) { eng.seekTo((int) (f * eng.duration())); updateProgress(); }
        });
        sr.addView(timeL);
        sr.addView(seek, new LinearLayout.LayoutParams(0, dp(26), 1f));
        sr.addView(timeR);
        col.addView(sr, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(26)));

        LinearLayout ctl = new LinearLayout(getContext());
        ctl.setOrientation(LinearLayout.HORIZONTAL);
        ctl.setGravity(Gravity.CENTER);
        shuf = btn(FegUi.SHUFFLE, 36, 0.5f, new OnClick() { public void go() { eng.toggleShuffle(); } });
        prev = btn(FegUi.PREV, 40, 0.6f, new OnClick() { public void go() { eng.prev(); } });
        play = btn(FegUi.PLAY, 48, 0.6f, new OnClick() { public void go() { onPlayClick(); } });
        next = btn(FegUi.NEXT, 40, 0.6f, new OnClick() { public void go() { eng.next(); } });
        rep = btn(FegUi.REPEAT, 36, 0.5f, new OnClick() { public void go() { eng.cycleRepeat(); } });
        play.setBackground(FegUi.round(FegUi.ACCENT, dp(40)));
        ctl.addView(shuf, new LinearLayout.LayoutParams(0, dp(44), 1f));
        ctl.addView(prev, new LinearLayout.LayoutParams(0, dp(44), 1f));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(dp(48), dp(48));
        ctl.addView(play, pl);
        ctl.addView(next, new LinearLayout.LayoutParams(0, dp(44), 1f));
        ctl.addView(rep, new LinearLayout.LayoutParams(0, dp(44), 1f));
        col.addView(ctl, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
    }

    // ---------------------------------------------------------------- state

    @Override public void onChange() { refresh(); }

    private void refresh() {
        FegEngine.Track t = eng.current();
        if (!eng.hasPermission()) {
            title.setText("Allow music access");
            artist.setText("Tap play to let FEG-PLAY find your songs");
        } else if (eng.loading && t == null) {
            title.setText("Looking for music...");
            artist.setText("");
        } else if (t == null) {
            title.setText(eng.loaded && eng.library.isEmpty() ? "No music found" : "FEG-PLAY");
            artist.setText(eng.loaded && eng.library.isEmpty() ? "Tap play to scan again" : "Tap play to start your music");
        } else {
            title.setText(t.title);
            artist.setText(t.artist);
        }
        play.set(eng.isPlaying() ? FegUi.PAUSE : FegUi.PLAY, Color.WHITE);
        if (!compact) {
            shuf.set(FegUi.SHUFFLE, eng.shuffle ? FegUi.ACCENT : FegUi.OFF);
            rep.set(eng.repeat == FegEngine.REPEAT_ONE ? FegUi.REPEAT1 : FegUi.REPEAT,
                    eng.repeat == FegEngine.REPEAT_OFF ? FegUi.OFF : FegUi.ACCENT);
        }
        loadArt(t);
        updateProgress();
    }

    private void loadArt(final FegEngine.Track t) {
        Object tag = t == null ? "none" : (Object) Long.valueOf(t.id);
        if (tag.equals(artTag)) return;
        artTag = tag;
        art.setImageDrawable(new FegUi.ArtPlaceholder(t == null ? "FEG-PLAY" : t.album));
        if (t == null) return;
        final Object mine = tag;
        eng.art(t, 256, new FegEngine.ArtCb() {
            public void on(Bitmap b) { if (b != null && mine.equals(artTag)) art.setImageBitmap(b); }
        });
    }

    private void updateProgress() {
        int d = eng.duration();
        int p = eng.position();
        if (seek != null && !seek.isDragging()) seek.setFrac(d > 0 ? p / (float) d : 0f);
        if (timeL != null && (seek == null || !seek.isDragging())) {
            timeL.setText(FegUi.fmt(p));
            timeR.setText(FegUi.fmt(d));
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        eng.addListener(this);
        if (eng.hasPermission()) eng.loadLibrary(false);
        artTag = null;
        refresh();
        removeCallbacks(tick);
        post(tick);
    }

    @Override protected void onDetachedFromWindow() {
        eng.removeListener(this);
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }
}
