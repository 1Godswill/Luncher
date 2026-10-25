package com.camon18i.voltage;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Builds flat / glass icon tiles. Style 0 (3D clay) is handled by MainActivity.composeIcon. */
final class IconStyler {
    static final int CLAY = 0, ROUND = 1, SQUARE = 2, SQUIRCLE = 3, GLASS = 4;
    static final String[] NAMES = {"3D", "Round", "Square", "Squircle", "Glass"};

    private IconStyler() {}

    static Path shape(int style, RectF r) {
        Path p = new Path();
        if (style == ROUND) {
            p.addOval(r, Path.Direction.CW);
        } else if (style == SQUARE) {
            float q = r.width() * 0.12f;
            p.addRoundRect(r, q, q, Path.Direction.CW);
        } else {
            // squircle: superellipse |x|^4 + |y|^4 = 1
            final int n = 72;
            float cx = r.centerX(), cy = r.centerY(), a = r.width() / 2f, b = r.height() / 2f;
            for (int i = 0; i <= n; i++) {
                double t = 2 * Math.PI * i / n;
                double c = Math.cos(t), s = Math.sin(t);
                float x = (float) (cx + a * Math.signum(c) * Math.pow(Math.abs(c), 0.5));
                float y = (float) (cy + b * Math.signum(s) * Math.pow(Math.abs(s), 0.5));
                if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
            }
            p.close();
        }
        return p;
    }

    /** Draws the app's own icon into an S x S bitmap (adaptive icons are zoomed to their safe area). */
    static Bitmap drawFace(Drawable d, int s) {
        Bitmap face = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas fc = new Canvas(face);
        try {
            if (d != null) {
                if (android.os.Build.VERSION.SDK_INT >= 26 && d instanceof android.graphics.drawable.AdaptiveIconDrawable) {
                    android.graphics.drawable.AdaptiveIconDrawable ad = (android.graphics.drawable.AdaptiveIconDrawable) d;
                    int o = (int) (s * 0.25f);
                    Drawable bg = ad.getBackground(), fg = ad.getForeground();
                    if (bg != null) { bg.setBounds(-o, -o, s + o, s + o); bg.draw(fc); }
                    if (fg != null) { fg.setBounds(-o, -o, s + o, s + o); fg.draw(fc); }
                } else {
                    d.setBounds(0, 0, s, s);
                    d.draw(fc);
                }
            }
        } catch (Exception ignored) {}
        return face;
    }

    private static void shadow(Canvas c, Path sp) {
        Paint sh = new Paint(Paint.ANTI_ALIAS_FLAG);
        sh.setColor(0x66000000);
        sh.setMaskFilter(new BlurMaskFilter(8, BlurMaskFilter.Blur.NORMAL));
        c.save();
        c.translate(0, 8);
        c.drawPath(sp, sh);
        c.restore();
    }

    private static void rim(Canvas c, Path sp, int top, int bottom) {
        Paint rp = new Paint(Paint.ANTI_ALIAS_FLAG);
        rp.setStyle(Paint.Style.STROKE);
        rp.setStrokeWidth(3);
        rp.setShader(new LinearGradient(0, 14, 0, 234, top, bottom, Shader.TileMode.CLAMP));
        c.drawPath(sp, rp);
    }

    private static void tileFill(Canvas c, Path sp, RectF t, boolean glass) {
        Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (glass) f.setShader(new LinearGradient(t.left, t.top, t.right, t.bottom, 0x73FFFFFF, 0x26FFFFFF, Shader.TileMode.CLAMP));
        else f.setShader(new LinearGradient(0, t.top, 0, t.bottom, 0xFFFAFBFF, 0xFFD5D9E6, Shader.TileMode.CLAMP));
        c.drawPath(sp, f);
    }

    private static void gloss(Canvas c, Path sp, RectF t) {
        c.save();
        c.clipPath(sp);
        Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
        g.setShader(new LinearGradient(0, t.top, 0, t.top + t.height() * 0.5f, 0x55FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(t.left, t.top, t.right, t.top + t.height() * 0.5f, g);
        c.restore();
    }

    /** A 3D clay object (transparent png) placed on a styled tile. art may be null for an empty tile. */
    static Bitmap tileFromArt(Bitmap art, int style) {
        Bitmap out = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        RectF t = new RectF(18, 14, 238, 234);
        Path sp = shape(style, t);
        boolean glass = style == GLASS;
        shadow(c, sp);
        tileFill(c, sp, t, glass);
        gloss(c, sp, t);
        rim(c, sp, glass ? 0xCCFFFFFF : 0x99FFFFFF, glass ? 0x33FFFFFF : 0x44000000);
        if (art != null) {
            float sz = 220 * 0.74f;
            RectF dst = new RectF(t.centerX() - sz / 2, t.centerY() - sz / 2 - 2, t.centerX() + sz / 2, t.centerY() + sz / 2 - 2);
            Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            c.drawBitmap(art, null, dst, bp);
        }
        return out;
    }

    /** A real app icon (from the system) placed on / cut into a styled tile. */
    static Bitmap tileFromDrawable(Drawable d, int style) {
        Bitmap out = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        RectF t = new RectF(18, 14, 238, 234);
        Path sp = shape(style, t);
        Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        shadow(c, sp);
        if (style == GLASS) {
            tileFill(c, sp, t, true);
            gloss(c, sp, t);
            float sz = 220 * 0.68f;
            RectF in = new RectF(t.centerX() - sz / 2, t.centerY() - sz / 2, t.centerX() + sz / 2, t.centerY() + sz / 2);
            Bitmap face = drawFace(d, 192);
            c.save();
            c.clipPath(shape(SQUIRCLE, in));
            c.drawBitmap(face, null, in, bp);
            c.restore();
            rim(c, sp, 0xCCFFFFFF, 0x33FFFFFF);
        } else {
            Bitmap face = drawFace(d, 220);
            c.save();
            c.clipPath(sp);
            c.drawBitmap(face, null, t, bp);
            c.restore();
            gloss(c, sp, t);
            rim(c, sp, 0x88FFFFFF, 0x44000000);
        }
        return out;
    }
}
