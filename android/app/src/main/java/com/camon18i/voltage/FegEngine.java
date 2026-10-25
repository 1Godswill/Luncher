package com.camon18i.voltage;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.LruCache;
import android.util.Size;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FEG-PLAY playback engine (one per process): music library, queue, MediaPlayer, audio focus,
 * media session (lock screen / headset buttons) and the data for the notification.
 * The home widget (FegPlayerView) and the full screen (FegPlayerScreen) only draw this state.
 */
public class FegEngine {

    static class Track {
        long id, albumId, duration;
        String title = "", artist = "", album = "";
        Uri uri;
    }

    interface Listener { void onChange(); }
    interface ArtCb { void on(Bitmap b); }

    private static FegEngine inst;

    static synchronized FegEngine get(Context c) {
        if (inst == null) inst = new FegEngine(c.getApplicationContext());
        return inst;
    }

    static final int REPEAT_OFF = 0, REPEAT_ALL = 1, REPEAT_ONE = 2;

    final Context app;
    private final SharedPreferences sp;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final AudioManager am;

    /** every song found on the phone, A to Z */
    final List<Track> library = new ArrayList<>();
    /** the songs in the order they will play */
    final List<Track> queue = new ArrayList<>();
    private final List<Track> base = new ArrayList<>();   // the same songs in list order (used when shuffle is turned off)
    private final Set<Long> favs = new HashSet<>();
    private final List<Listener> listeners = new ArrayList<>();

    int pos = -1;
    int repeat = REPEAT_OFF;
    boolean shuffle;
    boolean loaded, loading;
    /** goes up whenever the library, favourites or queue change, so lists know to rebuild */
    int version = 0;

    private MediaPlayer mp;
    private boolean prepared, playing, wantPlay, resumeOnFocus, hasFocus, ducked;
    private int pendingSeek = 0, errorRun = 0;
    private long sleepAt = 0;
    private long lastScan = 0;
    private MediaSession session;
    private AudioFocusRequest focusReq;
    private final Random rnd = new Random();

    private final LruCache<String, Bitmap> artCache = new LruCache<>(64);
    private final ExecutorService artEx = Executors.newFixedThreadPool(2);

    private FegEngine(Context c) {
        app = c;
        sp = c.getSharedPreferences("feg_play", Context.MODE_PRIVATE);
        am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        shuffle = sp.getBoolean("shuffle", false);
        repeat = Math.max(0, Math.min(2, sp.getInt("repeat", 0)));
        String f = sp.getString("favs", "");
        for (String s : f.split(",")) {
            if (s.length() == 0) continue;
            try { favs.add(Long.parseLong(s)); } catch (NumberFormatException ignored) {}
        }
        try {
            session = new MediaSession(c, "FEG-PLAY");
            session.setCallback(new MediaSession.Callback() {
                @Override public void onPlay() { play(); }
                @Override public void onPause() { pause(); }
                @Override public void onSkipToNext() { next(); }
                @Override public void onSkipToPrevious() { prev(); }
                @Override public void onStop() { pause(); }
                @Override public void onSeekTo(long p) { seekTo((int) p); }
            });
            session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
            session.setActive(true);
        } catch (Exception e) { session = null; }
        try {
            c.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context ctx, Intent i) {
                    if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(i.getAction())) pause();
                }
            }, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        } catch (Exception ignored) {}
    }

    // ================================================================ listeners

    void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    void removeListener(Listener l) { listeners.remove(l); }

    private void fire() {
        for (Listener l : new ArrayList<>(listeners)) {
            try { l.onChange(); } catch (Exception ignored) {}
        }
    }

    /** state changed: tell lock screen, notification and every open player view */
    private void changed() {
        publishSession();
        syncService();
        fire();
    }

    // ================================================================ library

    static String audioPermission() {
        return Build.VERSION.SDK_INT >= 33 ? "android.permission.READ_MEDIA_AUDIO"
                : "android.permission.READ_EXTERNAL_STORAGE";
    }

    boolean hasPermission() {
        return app.checkSelfPermission(audioPermission()) == PackageManager.PERMISSION_GRANTED;
    }

    /** Reads the songs on the phone (in the background). force = scan again even if we just did. */
    void loadLibrary(boolean force) {
        if (loading || !hasPermission()) return;
        if (!force && loaded && SystemClock.elapsedRealtime() - lastScan < 60000) return;
        loading = true;
        fire();
        new Thread(new Runnable() {
            public void run() {
                final List<Track> out = new ArrayList<>();
                try { out.addAll(queryLibrary()); } catch (Exception ignored) {}
                ui.post(new Runnable() {
                    public void run() {
                        library.clear();
                        library.addAll(out);
                        loaded = true;
                        loading = false;
                        lastScan = SystemClock.elapsedRealtime();
                        version++;
                        restoreLast();
                        fire();
                    }
                });
            }
        }, "feg-scan").start();
    }

    private List<Track> queryLibrary() {
        List<Track> out = new ArrayList<>();
        String[] cols = {
                MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION };
        Cursor c = null;
        try {
            c = app.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols,
                    MediaStore.Audio.Media.IS_MUSIC + " != 0 AND " + MediaStore.Audio.Media.DURATION + " >= 10000",
                    null, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC");
            if (c == null) return out;
            while (c.moveToNext()) {
                Track t = new Track();
                t.id = c.getLong(0);
                t.title = clean(c.getString(1), "Unknown song");
                t.artist = clean(c.getString(2), "Unknown artist");
                t.album = clean(c.getString(3), "Unknown album");
                t.albumId = c.getLong(4);
                t.duration = c.getLong(5);
                t.uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, t.id);
                out.add(t);
            }
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    private static String clean(String s, String fallback) {
        if (s == null) return fallback;
        s = s.trim();
        if (s.length() == 0 || s.equals("<unknown>")) return fallback;
        return s;
    }

    /** After a library scan: get the last song ready (paused) where the user left it. */
    private void restoreLast() {
        if (!queue.isEmpty() || library.isEmpty()) return;
        long id = sp.getLong("last_id", -1);
        if (id < 0) return;
        for (Track t : library) {
            if (t.id == id) {
                base.clear();
                base.addAll(library);
                rebuildQueue(t);
                pendingSeek = Math.max(0, sp.getInt("last_pos", 0));
                publishSession();
                return;
            }
        }
    }

    // ================================================================ queue

    Track current() { return pos >= 0 && pos < queue.size() ? queue.get(pos) : null; }

    private void rebuildQueue(Track cur) {
        queue.clear();
        if (shuffle && cur != null) {
            List<Track> rest = new ArrayList<>(base);
            rest.remove(cur);
            Collections.shuffle(rest, rnd);
            queue.add(cur);
            queue.addAll(rest);
            pos = 0;
        } else {
            queue.addAll(base);
            pos = cur == null ? -1 : queue.indexOf(cur);
        }
        version++;
    }

    /** Play a list of songs starting at one of them. */
    void playList(List<Track> list, int index) {
        if (list.isEmpty() || index < 0 || index >= list.size()) return;
        base.clear();
        base.addAll(list);
        rebuildQueue(list.get(index));
        startAt(pos, true, 0);
    }

    /** Play the whole library (random first song when shuffle is on). */
    void playAll() {
        if (library.isEmpty()) return;
        playList(library, shuffle ? rnd.nextInt(library.size()) : 0);
    }

    void playNext(Track t) {
        if (queue.isEmpty()) { List<Track> one = new ArrayList<>(); one.add(t); playList(one, 0); return; }
        queue.add(pos + 1, t);
        Track cur = current();
        int bi = cur == null ? -1 : base.indexOf(cur);
        base.add(bi >= 0 ? bi + 1 : base.size(), t);
        version++;
        fire();
    }

    void addToQueue(Track t) {
        if (queue.isEmpty()) { List<Track> one = new ArrayList<>(); one.add(t); playList(one, 0); return; }
        queue.add(t);
        base.add(t);
        version++;
        fire();
    }

    /** The song that is playing cannot be removed. */
    boolean removeFromQueue(int i) {
        if (i < 0 || i >= queue.size() || i == pos) return false;
        Track t = queue.remove(i);
        base.remove(t);
        if (i < pos) pos--;
        version++;
        fire();
        return true;
    }

    void jumpTo(int i) { if (i >= 0 && i < queue.size()) startAt(i, true, 0); }

    // ================================================================ playback

    private void startAt(int index, boolean autoplay, int seekMs) {
        if (index < 0 || index >= queue.size()) return;
        pos = index;
        final Track t = queue.get(pos);
        release();
        pendingSeek = 0;
        wantPlay = autoplay;
        final MediaPlayer p = new MediaPlayer();
        mp = p;
        final int seek = seekMs;
        try {
            p.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            p.setDataSource(app, t.uri);
            p.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override public void onPrepared(MediaPlayer m) {
                    if (m != mp) return;
                    prepared = true;
                    errorRun = 0;
                    if (seek > 0) { try { m.seekTo(seek); } catch (Exception ignored) {} }
                    if (wantPlay && requestFocus()) {
                        try { m.setVolume(ducked ? 0.25f : 1f, ducked ? 0.25f : 1f); m.start(); playing = true; } catch (Exception ignored) {}
                        startSaver();
                    } else {
                        wantPlay = false;
                    }
                    changed();
                }
            });
            p.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer m) {
                    if (m != mp) return;
                    playing = false;
                    onTrackEnded();
                }
            });
            p.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override public boolean onError(MediaPlayer m, int what, int extra) {
                    if (m != mp) return true;
                    onTrackError();
                    return true;
                }
            });
            p.prepareAsync();
        } catch (Exception e) {
            onTrackError();
            return;
        }
        art(t, 512, new ArtCb() { public void on(Bitmap b) { if (current() == t && b != null) changed(); } });
        savePos();
        changed();
    }

    private void onTrackEnded() {
        if (repeat == REPEAT_ONE) { startAt(pos, true, 0); return; }
        if (pos + 1 < queue.size()) { startAt(pos + 1, true, 0); return; }
        if (repeat == REPEAT_ALL && !queue.isEmpty()) { startAt(0, true, 0); return; }
        // end of the list: stop and get the last song ready again from the start
        wantPlay = false;
        playing = false;
        if (mp != null && prepared) { try { mp.seekTo(0); } catch (Exception ignored) {} }
        abandonFocus();
        changed();
    }

    /** A file cannot be played (deleted, damaged): skip it, but never loop forever. */
    private void onTrackError() {
        errorRun++;
        release();
        if (errorRun < 5 && pos + 1 < queue.size() && wantPlay) { startAt(pos + 1, true, 0); return; }
        wantPlay = false;
        playing = false;
        changed();
    }

    private void release() {
        MediaPlayer p = mp;
        mp = null;
        prepared = false;
        playing = false;
        if (p != null) {
            try { p.setOnPreparedListener(null); p.setOnCompletionListener(null); p.setOnErrorListener(null); } catch (Exception ignored) {}
            try { p.reset(); } catch (Exception ignored) {}
            try { p.release(); } catch (Exception ignored) {}
        }
    }

    boolean isPlaying() { return playing || (mp != null && !prepared && wantPlay); }

    void togglePlay() { if (isPlaying()) pause(); else play(); }

    void play() {
        if (queue.isEmpty()) { playAll(); return; }
        if (mp == null) { startAt(pos < 0 ? 0 : pos, true, pendingSeek); return; }
        wantPlay = true;
        if (!prepared) { changed(); return; }
        if (playing) return;
        if (!requestFocus()) return;
        try { mp.setVolume(ducked ? 0.25f : 1f, ducked ? 0.25f : 1f); mp.start(); playing = true; } catch (Exception ignored) {}
        startSaver();
        changed();
    }

    void pause() {
        wantPlay = false;
        resumeOnFocus = false;
        doPause();
        abandonFocus();
        changed();
    }

    private void doPause() {
        if (mp != null && prepared && playing) {
            try { mp.pause(); } catch (Exception ignored) {}
        }
        playing = false;
        savePos();
    }

    void next() {
        if (queue.isEmpty()) return;
        startAt(pos + 1 < queue.size() ? pos + 1 : 0, true, 0);
    }

    void prev() {
        if (queue.isEmpty()) return;
        if (position() > 3000) { seekTo(0); return; }
        startAt(pos > 0 ? pos - 1 : queue.size() - 1, true, 0);
    }

    void seekTo(int ms) {
        ms = Math.max(0, ms);
        if (mp != null && prepared) { try { mp.seekTo(ms); } catch (Exception ignored) {} }
        else pendingSeek = ms;
        publishSession();
    }

    int position() {
        if (mp != null && prepared) { try { return mp.getCurrentPosition(); } catch (Exception ignored) {} }
        return pendingSeek;
    }

    int duration() {
        if (mp != null && prepared) { try { int d = mp.getDuration(); if (d > 0) return d; } catch (Exception ignored) {} }
        Track t = current();
        return t == null ? 0 : (int) t.duration;
    }

    void setShuffle(boolean on) {
        shuffle = on;
        sp.edit().putBoolean("shuffle", on).apply();
        Track cur = current();
        if (cur != null) rebuildQueue(cur);
        changed();
    }

    void toggleShuffle() { setShuffle(!shuffle); }

    void cycleRepeat() {
        repeat = (repeat + 1) % 3;
        sp.edit().putInt("repeat", repeat).apply();
        changed();
    }

    // ---- favourites

    boolean isFav(Track t) { return t != null && favs.contains(t.id); }

    void toggleFav(Track t) {
        if (t == null) return;
        if (!favs.remove(t.id)) favs.add(t.id);
        StringBuilder sb = new StringBuilder();
        for (Long l : favs) sb.append(l).append(',');
        sp.edit().putString("favs", sb.toString()).apply();
        version++;
        fire();
    }

    // ---- sleep timer

    private final Runnable sleepRun = new Runnable() {
        public void run() { sleepAt = 0; pause(); }
    };

    void setSleep(int minutes) {
        ui.removeCallbacks(sleepRun);
        if (minutes <= 0) { sleepAt = 0; fire(); return; }
        sleepAt = SystemClock.elapsedRealtime() + minutes * 60000L;
        ui.postDelayed(sleepRun, minutes * 60000L);
        fire();
    }

    /** minutes left (rounded up) or 0 when no timer */
    int sleepLeftMin() {
        if (sleepAt == 0) return 0;
        long left = sleepAt - SystemClock.elapsedRealtime();
        return left <= 0 ? 0 : (int) ((left + 59999) / 60000);
    }

    // ---- remember where the user was

    private void savePos() {
        Track t = current();
        if (t == null) return;
        sp.edit().putLong("last_id", t.id).putInt("last_pos", position()).apply();
    }

    private final Runnable saveRun = new Runnable() {
        public void run() {
            if (!playing) return;
            savePos();
            ui.postDelayed(this, 10000);
        }
    };

    private void startSaver() { ui.removeCallbacks(saveRun); ui.postDelayed(saveRun, 10000); }

    // ================================================================ audio focus

    private final AudioManager.OnAudioFocusChangeListener focusL = new AudioManager.OnAudioFocusChangeListener() {
        @Override public void onAudioFocusChange(int change) {
            switch (change) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    hasFocus = true;
                    ducked = false;
                    if (mp != null && prepared) { try { mp.setVolume(1f, 1f); } catch (Exception ignored) {} }
                    if (resumeOnFocus) { resumeOnFocus = false; play(); }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    hasFocus = false;
                    resumeOnFocus = false;
                    wantPlay = false;
                    doPause();
                    changed();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    hasFocus = false;
                    resumeOnFocus = playing;
                    doPause();
                    changed();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    ducked = true;
                    if (mp != null && prepared) { try { mp.setVolume(0.25f, 0.25f); } catch (Exception ignored) {} }
                    break;
                default: break;
            }
        }
    };

    private boolean requestFocus() {
        if (hasFocus) return true;
        if (am == null) return true;
        int r;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                if (focusReq == null) {
                    focusReq = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                            .setAudioAttributes(new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                            .setOnAudioFocusChangeListener(focusL, ui).build();
                }
                r = am.requestAudioFocus(focusReq);
            } else {
                r = am.requestAudioFocus(focusL, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            }
        } catch (Exception e) { return true; }
        hasFocus = r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        return hasFocus;
    }

    private void abandonFocus() {
        if (am == null || !hasFocus) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) { if (focusReq != null) am.abandonAudioFocusRequest(focusReq); }
            else am.abandonAudioFocus(focusL);
        } catch (Exception ignored) {}
        hasFocus = false;
        ducked = false;
    }

    // ================================================================ album art

    Bitmap cachedArt(Track t, int px) { return t == null ? null : artCache.get(t.id + "@" + px); }

    /** Album art, loaded in the background. The callback gets null when the song has no picture. */
    void art(final Track t, final int px, final ArtCb cb) {
        if (t == null) { cb.on(null); return; }
        final String k = t.id + "@" + px;
        Bitmap b = artCache.get(k);
        if (b != null) { cb.on(b); return; }
        artEx.execute(new Runnable() {
            public void run() {
                final Bitmap r = loadArt(t, px);
                if (r != null) artCache.put(k, r);
                ui.post(new Runnable() { public void run() { cb.on(r); } });
            }
        });
    }

    private Bitmap loadArt(Track t, int px) {
        Bitmap r = null;
        if (Build.VERSION.SDK_INT >= 29) {
            try { r = app.getContentResolver().loadThumbnail(t.uri, new Size(px, px), null); } catch (Exception ignored) {}
        }
        if (r == null) {
            try {
                Uri au = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), t.albumId);
                InputStream in = app.getContentResolver().openInputStream(au);
                if (in != null) {
                    try {
                        ByteArrayOutputStream bo = new ByteArrayOutputStream();
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                        r = decode(bo.toByteArray(), px);
                    } finally { in.close(); }
                }
            } catch (Exception ignored) {}
        }
        if (r == null) {
            MediaMetadataRetriever m = new MediaMetadataRetriever();
            try {
                m.setDataSource(app, t.uri);
                byte[] d = m.getEmbeddedPicture();
                if (d != null) r = decode(d, px);
            } catch (Exception ignored) {
            } finally {
                try { m.release(); } catch (Exception ignored) {}
            }
        }
        return r;
    }

    private static Bitmap decode(byte[] d, int px) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(d, 0, d.length, o);
        int s = 1;
        while (o.outWidth / (s * 2) >= px && o.outHeight / (s * 2) >= px) s *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = s;
        return BitmapFactory.decodeByteArray(d, 0, d.length, o);
    }

    // ================================================================ lock screen, notification, service

    private void publishSession() {
        if (session == null) return;
        try {
            Track t = current();
            if (t == null) return;
            MediaMetadata.Builder mb = new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, t.title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, t.artist)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, t.album)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, duration());
            Bitmap a = artCache.get(t.id + "@512");
            if (a != null) mb.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, a);
            session.setMetadata(mb.build());
            int st = isPlaying() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
            session.setPlaybackState(new PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                            | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                            | PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_STOP)
                    .setState(st, position(), isPlaying() ? 1f : 0f)
                    .build());
            session.setActive(true);
        } catch (Exception ignored) {}
    }

    /** Keep the playback service (notification with controls) in step with the player. */
    private void syncService() {
        try {
            FegPlayService s = FegPlayService.instance;
            if (s != null) { s.refresh(false); return; }
            if (isPlaying() && current() != null) {
                Intent i = new Intent(app, FegPlayService.class);
                if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(i); else app.startService(i);
            }
        } catch (Exception ignored) {}
    }

    static final String CHANNEL = "feg_play";

    private PendingIntent action(Context c, String act, int code) {
        Intent i = new Intent(c, FegPlayService.class).setAction(act);
        return PendingIntent.getService(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    Notification buildNotification(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, "FEG-PLAY music", NotificationManager.IMPORTANCE_LOW);
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        Track t = current();
        boolean pl = isPlaying();
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        b.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(t == null ? "FEG-PLAY" : t.title)
                .setContentText(t == null ? "" : t.artist)
                .setSubText("FEG-PLAY")
                .setContentIntent(PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                .setDeleteIntent(action(c, FegPlayService.A_STOP, 5))
                .setOngoing(pl)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC);
        Bitmap art = t == null ? null : artCache.get(t.id + "@512");
        if (art != null) b.setLargeIcon(art);
        b.addAction(new Notification.Action.Builder(android.R.drawable.ic_media_previous, "Previous", action(c, FegPlayService.A_PREV, 1)).build());
        if (pl) b.addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause, "Pause", action(c, FegPlayService.A_PAUSE, 2)).build());
        else b.addAction(new Notification.Action.Builder(android.R.drawable.ic_media_play, "Play", action(c, FegPlayService.A_PLAY, 3)).build());
        b.addAction(new Notification.Action.Builder(android.R.drawable.ic_media_next, "Next", action(c, FegPlayService.A_NEXT, 4)).build());
        Notification.MediaStyle style = new Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2);
        if (session != null) style.setMediaSession(session.getSessionToken());
        b.setStyle(style);
        return b.build();
    }
}
