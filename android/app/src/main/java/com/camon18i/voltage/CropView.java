package com.camon18i.voltage;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/** Drag and pinch a picture under a screen-shaped frame; result() returns exactly what is inside the frame. */
public class CropView extends View {
    private final Bitmap src;
    private final int outW, outH;
    private final Matrix m = new Matrix();
    private final RectF frame = new RectF();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint dim = new Paint();
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector sgd;
    private float minScale = 1f, lx, ly;

    public CropView(Context c, Bitmap src, int outW, int outH) {
        super(c);
        this.src = src;
        this.outW = outW;
        this.outH = outH;
        dim.setColor(0xBB000000);
        line.setColor(0xFFFFFFFF);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(3f);
        sgd = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                m.postScale(d.getScaleFactor(), d.getScaleFactor(), d.getFocusX(), d.getFocusY());
                clamp();
                invalidate();
                return true;
            }
        });
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        float margin = w * 0.07f;
        float maxW = w - 2 * margin, maxH = h - 2 * margin - w * 0.25f;
        float aspect = outW / (float) outH;
        float fw, fh;
        if (maxW / maxH > aspect) { fh = maxH; fw = fh * aspect; } else { fw = maxW; fh = fw / aspect; }
        float top = margin + (maxH - fh) / 2f;
        frame.set((w - fw) / 2f, top, (w + fw) / 2f, top + fh);
        minScale = Math.max(fw / src.getWidth(), fh / src.getHeight());
        m.setScale(minScale, minScale);
        m.postTranslate(frame.centerX() - src.getWidth() * minScale / 2f, frame.centerY() - src.getHeight() * minScale / 2f);
    }

    private float scale() {
        float[] v = new float[9];
        m.getValues(v);
        return v[Matrix.MSCALE_X];
    }

    private void clamp() {
        float s = scale();
        if (s < minScale) m.postScale(minScale / s, minScale / s, frame.centerX(), frame.centerY());
        else if (s > minScale * 6f) m.postScale(minScale * 6f / s, minScale * 6f / s, frame.centerX(), frame.centerY());
        RectF r = new RectF(0, 0, src.getWidth(), src.getHeight());
        m.mapRect(r);
        float dx = 0, dy = 0;
        if (r.left > frame.left) dx = frame.left - r.left;
        if (r.right < frame.right) dx = frame.right - r.right;
        if (r.top > frame.top) dy = frame.top - r.top;
        if (r.bottom < frame.bottom) dy = frame.bottom - r.bottom;
        m.postTranslate(dx, dy);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        sgd.onTouchEvent(e);
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) {
            lx = e.getX(); ly = e.getY();
        } else if (a == MotionEvent.ACTION_POINTER_UP) {
            int keep = e.getActionIndex() == 0 ? 1 : 0;
            lx = e.getX(keep); ly = e.getY(keep);
        } else if (a == MotionEvent.ACTION_MOVE && !sgd.isInProgress() && e.getPointerCount() == 1) {
            m.postTranslate(e.getX() - lx, e.getY() - ly);
            lx = e.getX(); ly = e.getY();
            clamp();
            invalidate();
        }
        return true;
    }

    @Override protected void onDraw(Canvas c) {
        c.drawBitmap(src, m, p);
        int w = getWidth(), h = getHeight();
        c.drawRect(0, 0, w, frame.top, dim);
        c.drawRect(0, frame.bottom, w, h, dim);
        c.drawRect(0, frame.top, frame.left, frame.bottom, dim);
        c.drawRect(frame.right, frame.top, w, frame.bottom, dim);
        c.drawRect(frame, line);
        line.setStrokeWidth(1.5f);
        for (int i = 1; i <= 2; i++) {
            float x = frame.left + frame.width() * i / 3f, y = frame.top + frame.height() * i / 3f;
            c.drawLine(x, frame.top, x, frame.bottom, line);
            c.drawLine(frame.left, y, frame.right, y, line);
        }
        line.setStrokeWidth(3f);
    }

    /** The part of the picture inside the frame, scaled to the screen size. */
    public Bitmap result() {
        Bitmap out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
        Matrix om = new Matrix(m);
        om.postTranslate(-frame.left, -frame.top);
        float k = outW / frame.width();
        om.postScale(k, k);
        new Canvas(out).drawBitmap(src, om, p);
        return out;
    }
}
