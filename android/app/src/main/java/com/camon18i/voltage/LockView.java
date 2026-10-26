package com.camon18i.voltage;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.TreeSet;

/**
 * Number-puzzle lock: glossy 3D numbers drift around at random. Tap them from the smallest to the biggest;
 * the biggest one opens the lock. A wrong tap resets the sequence. All balls are the same size; the number on the ball is what counts.
 */
public class LockView extends View {

    static class Ball {
        float x, y, vx, vy, r, phase, pop = -1f;
        int val, rank, order = -1;
        boolean done, target = true;
        Bitmap bmp;
    }

    private final List<Ball> balls = new ArrayList<>();
    private final Random rnd = new Random();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    static final int R_ASC = 0, R_DESC = 1, R_ODD = 2, R_EVEN = 3, R_ODD_ASC = 4, R_EVEN_ASC = 5, R_RANDOM = 6;
    private static final String[] RULE_HINTS = {
        "smallest first, biggest last", "biggest first, smallest last", "odd numbers only, any order",
        "even numbers only, any order", "odd numbers, smallest first", "even numbers, smallest first"};

    private final int count, speed, ruleUsed;
    private final boolean showHint;
    private final Runnable onPhonePin;
    private int need;
    private final String title;
    private final boolean lowFps;
    private final Runnable onSuccess, onCancel;
    private final float dens;
    private int progress = 0;
    private float shake = 0f;
    private boolean running = false, finished = false;
    private long last;
    private Shader bgShader;
    private final SimpleDateFormat clockFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public LockView(Context c, int count, int speed, int rule, boolean showHint, String title, boolean lowFps,
                    Runnable onSuccess, Runnable onCancel, Runnable onPhonePin) {
        super(c);
        this.count = Math.max(4, Math.min(9, count));
        this.speed = Math.max(1, Math.min(5, speed));
        rule = Math.max(0, Math.min(R_RANDOM, rule));
        this.ruleUsed = rule == R_RANDOM ? rnd.nextInt(R_RANDOM) : rule;
        this.showHint = showHint || rule == R_RANDOM;   // a random rule can't be remembered, so it always shows the hint
        this.onPhonePin = onPhonePin;
        this.title = title == null ? "" : title;
        this.lowFps = lowFps;
        this.onSuccess = onSuccess;
        this.onCancel = onCancel;
        this.dens = c.getResources().getDisplayMetrics().density;
        setClickable(true);
    }

    private float dp(float v) { return v * dens; }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            long now = SystemClock.uptimeMillis();
            float dt = Math.min(0.05f, (now - last) / 1000f);
            last = now;
            step(dt);
            invalidate();
            if (lowFps) postDelayed(this, 40); else postOnAnimation(this);
        }
    };

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        last = SystemClock.uptimeMillis();
        post(tick);
    }

    @Override protected void onDetachedFromWindow() {
        running = false;
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        bgShader = new LinearGradient(0, 0, 0, h, 0xFF0B0D1E, 0xFF1A0F2A, Shader.TileMode.CLAMP);
        if (balls.isEmpty() && w > 0 && h > 0) setup();
    }

    private Bitmap sphere(Ball b, int rank) {
        int s = (int) Math.ceil(b.r * 2);
        Bitmap bm = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        float hue = (200f + rank * (150f / Math.max(1, count - 1))) % 360f;
        int light = Color.HSVToColor(new float[]{hue, 0.25f, 1f});
        int mid = Color.HSVToColor(new float[]{hue, 0.65f, 0.95f});
        int dark = Color.HSVToColor(new float[]{hue, 0.85f, 0.50f});
        Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
        q.setShader(new RadialGradient(b.r * 0.7f, b.r * 0.62f, b.r * 1.25f, new int[]{light, mid, dark}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(b.r, b.r, b.r - 1, q);
        q.setShader(null);
        q.setColor(0x55FFFFFF);
        c.drawOval(new RectF(b.r * 0.38f, b.r * 0.2f, b.r * 1.1f, b.r * 0.7f), q);   // specular highlight
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextAlign(Paint.Align.CENTER);
        t.setTextSize(b.r * (b.val > 9 ? 0.78f : 0.95f));
        float ty = b.r - (t.descent() + t.ascent()) / 2f;
        t.setColor(0x66000000);
        c.drawText(String.valueOf(b.val), b.r + b.r * 0.04f, ty + b.r * 0.05f, t);
        t.setColor(Color.WHITE);
        c.drawText(String.valueOf(b.val), b.r, ty, t);
        return bm;
    }

    private void setup() {
        balls.clear();
        progress = 0;
        boolean parityRule = ruleUsed >= R_ODD;
        int wantOdd = (ruleUsed == R_ODD || ruleUsed == R_ODD_ASC) ? 1 : 0;
        TreeSet<Integer> vals = new TreeSet<>();
        if (!parityRule) {
            while (vals.size() < count) vals.add(1 + rnd.nextInt(99));
        } else {
            // some numbers with the wanted parity (the ones to tap) and at least one decoy of the other parity
            int t = Math.min(count - 1, Math.max(2, count / 2 + rnd.nextInt(2)));
            int got = 0;
            while (got < t) { int v = 1 + rnd.nextInt(99); if (v % 2 == wantOdd && vals.add(v)) got++; }
            while (vals.size() < count) { int v = 1 + rnd.nextInt(99); if (v % 2 != wantOdd) vals.add(v); }
        }
        int i = 0, targetIdx = 0;
        int w = getWidth(), h = getHeight();
        float top = dp(170);
        for (int v : vals) {
            Ball b = new Ball();
            b.val = v;
            b.rank = i;
            if (ruleUsed == R_ASC) { b.target = true; b.order = i; }
            else if (ruleUsed == R_DESC) { b.target = true; b.order = count - 1 - i; }
            else {
                b.target = (v % 2 == wantOdd);
                if (b.target && (ruleUsed == R_ODD_ASC || ruleUsed == R_EVEN_ASC)) b.order = targetIdx;
                if (b.target) targetIdx++;
            }
            b.r = dp(38);   // every ball is the same size
            b.x = b.r + rnd.nextFloat() * Math.max(1f, w - 2 * b.r);
            b.y = top + b.r + rnd.nextFloat() * Math.max(1f, h - top - dp(70) - 2 * b.r);
            double ang = rnd.nextDouble() * Math.PI * 2;
            float sp = dp(12 + 9 * speed) * (0.7f + rnd.nextFloat() * 0.6f);
            b.vx = (float) Math.cos(ang) * sp;
            b.vy = (float) Math.sin(ang) * sp;
            b.phase = rnd.nextFloat() * 6.28f;
            b.bmp = sphere(b, i);
            balls.add(b);
            i++;
        }
        need = parityRule ? targetIdx : count;
    }

    private void step(float dt) {
        int w = getWidth(), h = getHeight();
        float top = dp(150), maxV = dp(20 + 14 * speed), kick = dp(40 + 30 * speed);
        for (Ball b : balls) {
            b.phase += dt * 1.6f;
            if (b.pop >= 0f) { b.pop += dt * 4f; continue; }
            if (b.done) continue;
            b.vx += (rnd.nextFloat() - 0.5f) * kick * dt * 6f;
            b.vy += (rnd.nextFloat() - 0.5f) * kick * dt * 6f;
            float v = (float) Math.hypot(b.vx, b.vy);
            if (v > maxV) { b.vx *= maxV / v; b.vy *= maxV / v; }
            b.x += b.vx * dt;
            b.y += b.vy * dt;
            if (b.x < b.r) { b.x = b.r; b.vx = Math.abs(b.vx); }
            if (b.x > w - b.r) { b.x = w - b.r; b.vx = -Math.abs(b.vx); }
            if (b.y < top + b.r) { b.y = top + b.r; b.vy = Math.abs(b.vy); }
            if (b.y > h - dp(64) - b.r) { b.y = h - dp(64) - b.r; b.vy = -Math.abs(b.vy); }
        }
        if (shake > 0f) shake = Math.max(0f, shake - dt * 2.5f);
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        p.setShader(bgShader);
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        p.setColor(Color.WHITE);
        p.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(dp(56));
        c.drawText(clockFmt.format(new Date()), w / 2f, dp(92), p);
        p.setTypeface(Typeface.DEFAULT);
        p.setTextSize(dp(14));
        p.setColor(0xCCFFFFFF);
        if (title.length() > 0) c.drawText(title, w / 2f, dp(124), p);
        if (showHint) {
            p.setTextSize(dp(12));
            p.setColor(0x88FFFFFF);
            c.drawText(progress + " / " + need + "   (" + RULE_HINTS[ruleUsed] + ")", w / 2f, dp(144), p);
        }
        if (onPhonePin != null) {   // small white dot: tap to use the phone's own PIN / pattern instead
            p.setColor(0x33FFFFFF);
            c.drawCircle(w / 2f, h - dp(28), dp(9), p);
            p.setColor(0xDDFFFFFF);
            c.drawCircle(w / 2f, h - dp(28), dp(4.5f), p);
        }
        if (onCancel != null) {
            p.setTextSize(dp(24));
            p.setColor(0xCCFFFFFF);
            c.drawText("\u2715", w - dp(34), dp(46), p);
        }

        float sx = (float) Math.sin(shake * 40f) * dp(8) * shake;
        // bigger balls are "closer", so they are drawn last
        for (Ball b : balls) {
            if (b.done && b.pop >= 1f) continue;
            float bob = (float) Math.sin(b.phase) * b.r * 0.07f;
            float sc = 1f + 0.03f * (float) Math.sin(b.phase * 0.7f);
            float alpha = 1f;
            if (b.pop >= 0f) { sc *= 1f + 0.5f * b.pop; alpha = 1f - b.pop; }
            // shadow on the "floor"
            p.setColor(((int) (70 * alpha) << 24));
            c.drawOval(new RectF(b.x + sx - b.r * 0.8f, b.y + b.r * 1.15f + bob * 0.3f, b.x + sx + b.r * 0.8f, b.y + b.r * 1.4f), p);
            c.save();
            c.translate(b.x + sx, b.y + bob);
            c.scale(sc, sc);
            p.setAlpha((int) (255 * alpha));
            c.drawBitmap(b.bmp, -b.r, -b.r, p);
            p.setAlpha(255);
            c.restore();
        }
    }

    private void reset() {
        progress = 0;
        shake = 1f;
        for (Ball b : balls) { b.done = false; b.pop = -1f; }
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() != MotionEvent.ACTION_DOWN || finished) return true;
        float x = e.getX(), y = e.getY();
        if (onCancel != null && x > getWidth() - dp(70) && y < dp(76)) { finished = true; onCancel.run(); return true; }
        if (onPhonePin != null && Math.hypot(x - getWidth() / 2f, y - (getHeight() - dp(28))) < dp(30)) {
            onPhonePin.run();
            return true;
        }
        Ball hit = null;
        float best = 9999f;
        for (Ball b : balls) {
            if (b.done) continue;
            float d = (float) Math.hypot(x - b.x, y - b.y) / b.r;
            if (d < 1.15f && d < best) { best = d; hit = b; }
        }
        if (hit == null) return true;
        boolean right = hit.target && (hit.order < 0 || hit.order == progress);
        if (right) {
            hit.done = true;
            hit.pop = 0f;
            progress++;
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (progress >= need) {
                finished = true;
                postDelayed(new Runnable() { public void run() { if (onSuccess != null) onSuccess.run(); } }, 350);
            }
        } else {
            reset();
        }
        return true;
    }
}
