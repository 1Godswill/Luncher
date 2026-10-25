package com.camon18i.voltage;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import java.util.Calendar;

/** Live ticking clay clock icon. */
public class ClockView extends View {
    private final Bitmap face, hour, min, sec;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect dst = new Rect();

    public ClockView(Context c, Bitmap face, Bitmap hour, Bitmap min, Bitmap sec) {
        super(c);
        this.face = face; this.hour = hour; this.min = min; this.sec = sec;
    }

    private void layer(Canvas c, Bitmap b, float angle, float px, float py) {
        if (b == null) return;
        c.save();
        c.rotate(angle, px, py);
        c.drawBitmap(b, null, dst, p);
        c.restore();
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        dst.set(0, 0, w, h);
        Calendar cal = Calendar.getInstance();
        int hh = cal.get(Calendar.HOUR), mm = cal.get(Calendar.MINUTE), ss = cal.get(Calendar.SECOND), ms = cal.get(Calendar.MILLISECOND);
        if (face != null) c.drawBitmap(face, null, dst, p);
        float px = w * 0.5f, py = h * (140f / 256f);
        layer(c, hour, (hh + mm / 60f) * 30f, px, py);
        layer(c, min, (mm + ss / 60f) * 6f, px, py);
        layer(c, sec, ss * 6f, px, py);
        postInvalidateDelayed(1000 - ms);
    }
}
