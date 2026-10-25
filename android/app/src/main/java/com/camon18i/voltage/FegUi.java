package com.camon18i.voltage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;

/** Small drawing helpers for FEG-PLAY: vector icons, seek bar, album art placeholder. No image files needed. */
final class FegUi {
    private FegUi() {}

    static final int BG = 0xFF0E0F13, CARD = 0xFF1A1C23, CARD2 = 0xFF262932;
    static final int ACCENT = 0xFFE53935, TEXT = 0xFFF2F2F5, SUB = 0xFF9AA0AE, OFF = 0xCCFFFFFF;

    static final int PLAY = 0, PAUSE = 1, NEXT = 2, PREV = 3, SHUFFLE = 4, REPEAT = 5, REPEAT1 = 6, HEART = 7,
            HEART_ON = 8, BACK = 9, NOTE = 10, SEARCH = 11, TIMER = 12, LIST = 13, REFRESH = 14, VOLUME = 15,
            MORE = 16, CLOSE = 17;

    static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    static String fmt(long ms) {
        long s = Math.max(0, ms) / 1000;
        long m = s / 60;
        s = s % 60;
        if (m >= 60) return (m / 60) + ":" + (m % 60 < 10 ? "0" : "") + (m % 60) + ":" + (s < 10 ? "0" : "") + s;
        return m + ":" + (s < 10 ? "0" : "") + s;
    }

    static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    static void clipRound(View v, final float radiusPx) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline o) { o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx); }
        });
        v.setClipToOutline(true);
    }

    // ================================================================ icons

    /** Draws icon `type` inside a square of side `size` centred on (cx, cy). */
    static void drawIcon(Canvas c, int type, float cx, float cy, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        float u = size / 24f;
        c.save();
        c.translate(cx - size / 2f, cy - size / 2f);
        c.scale(u, u);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(2.2f);
        Path a = new Path();
        switch (type) {
            case PLAY:
                a.moveTo(7, 4.5f); a.lineTo(7, 19.5f); a.lineTo(19.5f, 12); a.close();
                p.setStyle(Paint.Style.FILL_AND_STROKE); p.setStrokeWidth(1.4f);
                c.drawPath(a, p);
                break;
            case PAUSE:
                p.setStyle(Paint.Style.FILL);
                c.drawRoundRect(new RectF(6, 4.5f, 10.2f, 19.5f), 1.2f, 1.2f, p);
                c.drawRoundRect(new RectF(13.8f, 4.5f, 18, 19.5f), 1.2f, 1.2f, p);
                break;
            case NEXT:
                a.moveTo(5.5f, 5); a.lineTo(5.5f, 19); a.lineTo(15.5f, 12); a.close();
                p.setStyle(Paint.Style.FILL_AND_STROKE); p.setStrokeWidth(1.2f);
                c.drawPath(a, p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.4f);
                c.drawLine(18.5f, 5.5f, 18.5f, 18.5f, p);
                break;
            case PREV:
                a.moveTo(18.5f, 5); a.lineTo(18.5f, 19); a.lineTo(8.5f, 12); a.close();
                p.setStyle(Paint.Style.FILL_AND_STROKE); p.setStrokeWidth(1.2f);
                c.drawPath(a, p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.4f);
                c.drawLine(5.5f, 5.5f, 5.5f, 18.5f, p);
                break;
            case SHUFFLE:
                p.setStyle(Paint.Style.STROKE);
                a.moveTo(3, 7); a.lineTo(7, 7); a.cubicTo(11, 7, 12, 17, 16, 17); a.lineTo(18, 17);
                a.moveTo(3, 17); a.lineTo(7, 17); a.cubicTo(11, 17, 12, 7, 16, 7); a.lineTo(18, 7);
                c.drawPath(a, p);
                p.setStyle(Paint.Style.FILL);
                Path h = new Path();
                h.moveTo(17.5f, 13.5f); h.lineTo(21.5f, 17); h.lineTo(17.5f, 20.5f); h.close();
                h.moveTo(17.5f, 3.5f); h.lineTo(21.5f, 7); h.lineTo(17.5f, 10.5f); h.close();
                c.drawPath(h, p);
                break;
            case REPEAT:
            case REPEAT1:
                p.setStyle(Paint.Style.STROKE);
                a.moveTo(4, 12); a.lineTo(4, 10.5f); a.quadTo(4, 8, 6.5f, 8); a.lineTo(16, 8);
                a.moveTo(20, 12); a.lineTo(20, 13.5f); a.quadTo(20, 16, 17.5f, 16); a.lineTo(8, 16);
                c.drawPath(a, p);
                p.setStyle(Paint.Style.FILL);
                Path r = new Path();
                r.moveTo(15.5f, 4.8f); r.lineTo(20, 8); r.lineTo(15.5f, 11.2f); r.close();
                r.moveTo(8.5f, 12.8f); r.lineTo(4, 16); r.lineTo(8.5f, 19.2f); r.close();
                c.drawPath(r, p);
                if (type == REPEAT1) {
                    p.setTypeface(Typeface.DEFAULT_BOLD);
                    p.setTextSize(9f);
                    p.setTextAlign(Paint.Align.CENTER);
                    c.drawText("1", 12, 14.2f, p);
                }
                break;
            case HEART:
            case HEART_ON:
                a.moveTo(12, 20.5f);
                a.cubicTo(5, 14.5f, 2.5f, 11.5f, 2.5f, 8.2f);
                a.cubicTo(2.5f, 5.4f, 4.7f, 3.5f, 7.2f, 3.5f);
                a.cubicTo(9.2f, 3.5f, 11, 4.6f, 12, 6.3f);
                a.cubicTo(13, 4.6f, 14.8f, 3.5f, 16.8f, 3.5f);
                a.cubicTo(19.3f, 3.5f, 21.5f, 5.4f, 21.5f, 8.2f);
                a.cubicTo(21.5f, 11.5f, 19, 14.5f, 12, 20.5f);
                a.close();
                p.setStyle(type == HEART_ON ? Paint.Style.FILL_AND_STROKE : Paint.Style.STROKE);
                c.drawPath(a, p);
                break;
            case BACK:
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.6f);
                a.moveTo(15, 4.5f); a.lineTo(7.5f, 12); a.lineTo(15, 19.5f);
                c.drawPath(a, p);
                break;
            case NOTE:
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(8.5f, 17.5f, 3.4f, p);
                c.drawRect(10.8f, 4.5f, 12.8f, 17.5f, p);
                a.moveTo(12.8f, 4.5f); a.quadTo(18.5f, 5.5f, 18.5f, 11); a.quadTo(16, 8.5f, 12.8f, 9.2f); a.close();
                c.drawPath(a, p);
                break;
            case SEARCH:
                p.setStyle(Paint.Style.STROKE);
                c.drawCircle(10, 10, 6, p);
                c.drawLine(14.5f, 14.5f, 20, 20, p);
                break;
            case TIMER:
                p.setStyle(Paint.Style.STROKE);
                c.drawCircle(12, 13.5f, 7.5f, p);
                c.drawLine(12, 13.5f, 12, 9, p);
                c.drawLine(12, 13.5f, 15, 14.8f, p);
                c.drawLine(9.5f, 3.2f, 14.5f, 3.2f, p);
                break;
            case LIST:
                p.setStyle(Paint.Style.STROKE);
                c.drawLine(4, 6.5f, 20, 6.5f, p);
                c.drawLine(4, 12, 20, 12, p);
                c.drawLine(4, 17.5f, 13, 17.5f, p);
                break;
            case REFRESH:
                p.setStyle(Paint.Style.STROKE);
                c.drawArc(new RectF(5, 5, 19, 19), -40, 290, false, p);
                p.setStyle(Paint.Style.FILL);
                a.moveTo(13.5f, 1.5f); a.lineTo(19.5f, 5.2f); a.lineTo(12.5f, 8.5f); a.close();
                c.drawPath(a, p);
                break;
            case VOLUME:
                p.setStyle(Paint.Style.FILL);
                a.moveTo(3.5f, 9.5f); a.lineTo(7.5f, 9.5f); a.lineTo(12.5f, 5); a.lineTo(12.5f, 19); a.lineTo(7.5f, 14.5f); a.lineTo(3.5f, 14.5f); a.close();
                c.drawPath(a, p);
                p.setStyle(Paint.Style.STROKE);
                c.drawArc(new RectF(10, 8, 18, 16), -50, 100, false, p);
                c.drawArc(new RectF(8, 4.5f, 21, 19.5f), -50, 100, false, p);
                break;
            case MORE:
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(12, 5, 1.9f, p);
                c.drawCircle(12, 12, 1.9f, p);
                c.drawCircle(12, 19, 1.9f, p);
                break;
            case CLOSE:
                p.setStyle(Paint.Style.STROKE);
                c.drawLine(6, 6, 18, 18, p);
                c.drawLine(18, 6, 6, 18, p);
                break;
            default: break;
        }
        c.restore();
    }

    /** A view that shows one icon. Change type / color at any time. */
    static class Icon extends View {
        private int type, color;
        private final float scale;

        Icon(Context c, int type, int color, float scale) {
            super(c);
            this.type = type;
            this.color = color;
            this.scale = scale;
        }

        void set(int type, int color) {
            if (this.type == type && this.color == color) return;
            this.type = type;
            this.color = color;
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight()) * scale;
            drawIcon(c, type, getWidth() / 2f, getHeight() / 2f, s, color);
        }
    }

    // ================================================================ seek bar

    interface SeekListener {
        void onScrub(float frac);    // finger moving
        void onCommit(float frac);   // finger lifted
    }

    /** Thin progress bar you can drag. Also stops the home pager from stealing the drag. */
    static class Seek extends View {
        private float frac;
        private boolean dragging, touchable = true;
        private SeekListener listener;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int fill = ACCENT, track = 0x33FFFFFF;
        private final boolean thumb;

        Seek(Context c, boolean thumb) {
            super(c);
            this.thumb = thumb;
        }

        void setListener(SeekListener l) { listener = l; }
        void setTouchable(boolean t) { touchable = t; }
        void setColors(int fill, int track) { this.fill = fill; this.track = track; invalidate(); }
        boolean isDragging() { return dragging; }

        void setFrac(float f) {
            f = Math.max(0f, Math.min(1f, f));
            if (f == frac) return;
            frac = f;
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            float h = getHeight(), w = getWidth();
            float th = Math.max(dp(getContext(), 3), h * 0.12f);
            if (!thumb) th = Math.min(h, dp(getContext(), 3));
            float pad = thumb ? dp(getContext(), 8) : 0;
            float l = pad, r = w - pad, y = h / 2f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(track);
            c.drawRoundRect(new RectF(l, y - th / 2, r, y + th / 2), th / 2, th / 2, paint);
            float x = l + (r - l) * frac;
            paint.setColor(fill);
            c.drawRoundRect(new RectF(l, y - th / 2, Math.max(l + th, x), y + th / 2), th / 2, th / 2, paint);
            if (thumb) {
                paint.setColor(Color.WHITE);
                c.drawCircle(x, y, dragging ? dp(getContext(), 8) : dp(getContext(), 6), paint);
            }
        }

        private float fracAt(float x) {
            float pad = thumb ? dp(getContext(), 8) : 0;
            float w = getWidth() - 2 * pad;
            return w <= 0 ? 0 : Math.max(0f, Math.min(1f, (x - pad) / w));
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (!touchable) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    dragging = true;
                    frac = fracAt(e.getX());
                    invalidate();
                    if (listener != null) listener.onScrub(frac);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!dragging) return true;
                    frac = fracAt(e.getX());
                    invalidate();
                    if (listener != null) listener.onScrub(frac);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragging) {
                        dragging = false;
                        frac = fracAt(e.getX());
                        invalidate();
                        if (listener != null) listener.onCommit(frac);
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    invalidate();
                    return true;
                default: return true;
            }
        }
    }

    // ================================================================ album art placeholder

    /** Colourful gradient with a music note, different for every album. */
    static class ArtPlaceholder extends Drawable {
        private final int c1, c2;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        ArtPlaceholder(String seed) {
            int h = Math.abs((seed == null ? "" : seed).hashCode());
            float hue = h % 360;
            c1 = Color.HSVToColor(new float[]{hue, 0.55f, 0.55f});
            c2 = Color.HSVToColor(new float[]{(hue + 40) % 360, 0.65f, 0.30f});
        }

        @Override public void draw(Canvas c) {
            android.graphics.Rect b = getBounds();
            p.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom, c1, c2, Shader.TileMode.CLAMP));
            c.drawRect(b, p);
            p.setShader(null);
            drawIcon(c, NOTE, b.exactCenterX(), b.exactCenterY(), Math.min(b.width(), b.height()) * 0.5f, 0xBBFFFFFF);
        }

        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(android.graphics.ColorFilter f) {}
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }
}
