package com.camon18i.voltage;

import android.app.WallpaperManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Decode, crop, blur, store and apply wallpapers. All methods are safe to call off the UI thread. */
final class WallpaperHelper {
    private WallpaperHelper() {}

    private static int sample(int w, int h, int maxDim) {
        int s = 1;
        while (Math.max(w, h) / (s * 2) >= maxDim) s *= 2;
        return s;
    }

    static Bitmap decodeRes(Resources r, int id, int maxDim) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(r, id, o);
        o.inSampleSize = sample(o.outWidth, o.outHeight, maxDim);
        o.inJustDecodeBounds = false;
        return BitmapFactory.decodeResource(r, id, o);
    }

    static Bitmap decodeUri(Context c, Uri uri, int maxDim) {
        try {
            ContentResolver cr = c.getContentResolver();
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream in = cr.openInputStream(uri);
            BitmapFactory.decodeStream(in, null, o);
            if (in != null) in.close();
            o.inSampleSize = sample(o.outWidth, o.outHeight, maxDim);
            o.inJustDecodeBounds = false;
            in = cr.openInputStream(uri);
            Bitmap b = BitmapFactory.decodeStream(in, null, o);
            if (in != null) in.close();
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    static Bitmap decodeFile(File f, int maxDim) {
        if (f == null || !f.exists()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        o.inSampleSize = sample(o.outWidth, o.outHeight, maxDim);
        o.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    static void saveJpeg(Bitmap b, File f) {
        try {
            FileOutputStream out = new FileOutputStream(f);
            b.compress(Bitmap.CompressFormat.JPEG, 94, out);
            out.close();
        } catch (Exception ignored) {}
    }

    /** Scales and crops the bitmap around its centre so it exactly fills w x h. */
    static Bitmap centerCrop(Bitmap src, int w, int h) {
        float s = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Matrix m = new Matrix();
        m.setScale(s, s);
        m.postTranslate((w - src.getWidth() * s) / 2f, (h - src.getHeight() * s) / 2f);
        new Canvas(out).drawBitmap(src, m, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        return out;
    }

    /** amount 0..100. Shrinks, box-blurs and scales back up: cheap and smooth enough for a wallpaper. */
    static Bitmap blur(Bitmap src, int amount) {
        if (amount <= 0) return src;
        int w = src.getWidth(), h = src.getHeight();
        float f = 1f + amount / 12f;
        int sw = Math.max(8, (int) (w / f)), sh = Math.max(8, (int) (h / f));
        Bitmap small = Bitmap.createScaledBitmap(src, sw, sh, true);
        int[] px = new int[sw * sh];
        small.getPixels(px, 0, sw, 0, 0, sw, sh);
        int r = 3;
        for (int pass = 0; pass < 2; pass++) {
            boxBlur(px, sw, sh, r, true);
            boxBlur(px, sw, sh, r, false);
        }
        Bitmap b = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
        b.setPixels(px, 0, sw, 0, 0, sw, sh);
        return Bitmap.createScaledBitmap(b, w, h, true);
    }

    private static void boxBlur(int[] px, int w, int h, int r, boolean horizontal) {
        int len = horizontal ? w : h, lines = horizontal ? h : w;
        int[] line = new int[len];
        int[] out = new int[len];
        for (int l = 0; l < lines; l++) {
            for (int i = 0; i < len; i++) line[i] = horizontal ? px[l * w + i] : px[i * w + l];
            for (int i = 0; i < len; i++) {
                int a = 0, rr = 0, g = 0, bb = 0, n = 0;
                int from = Math.max(0, i - r), to = Math.min(len - 1, i + r);
                for (int k = from; k <= to; k++) {
                    int p = line[k];
                    a += p >>> 24; rr += (p >> 16) & 255; g += (p >> 8) & 255; bb += p & 255; n++;
                }
                out[i] = ((a / n) << 24) | ((rr / n) << 16) | ((g / n) << 8) | (bb / n);
            }
            for (int i = 0; i < len; i++) { if (horizontal) px[l * w + i] = out[i]; else px[i * w + l] = out[i]; }
        }
    }

    /** Sets the system home and lock screen wallpaper. */
    static boolean apply(Context c, Bitmap b) {
        try {
            WallpaperManager wm = WallpaperManager.getInstance(c);
            if (android.os.Build.VERSION.SDK_INT >= 24)
                wm.setBitmap(b, null, true, WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK);
            else
                wm.setBitmap(b);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
