package com.crescent.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.core.content.ContextCompat;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

/** Open 270-degree ring used on the dashboard Zakat Status card. */
public class GaugeView extends View {
    private static final float START_ANGLE = 135f;
    private static final float SWEEP_ANGLE = 270f;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private float progress = 0f;      // 0..1
    private ValueAnimator animator;

    public GaugeView(Context c) { this(c, null); }
    public GaugeView(Context c, AttributeSet a) { this(c, a, 0); }
    public GaugeView(Context c, AttributeSet a, int s) {
        super(c, a, s);
        float stroke = 8f * getResources().getDisplayMetrics().density;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setStrokeWidth(stroke);
        track.setColor(ContextCompat.getColor(c, R.color.sage_ring_track));
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setStrokeWidth(stroke);
        arc.setColor(ContextCompat.getColor(c, R.color.sage_ring));
    }

    /** Sets the filled part of the ring (0..1), animated. */
    public void setProgress(float target) {
        target = Math.max(0f, Math.min(1f, target));
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(progress, target);
        animator.setDuration(700L);
        animator.setInterpolator(new FastOutSlowInInterpolator());
        animator.addUpdateListener(a -> { progress = (float) a.getAnimatedValue(); invalidate(); });
        animator.start();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) animator.cancel();
    }

    @Override protected void onDraw(Canvas canvas) {
        float pad = track.getStrokeWidth() / 2f + getPaddingLeft();
        oval.set(pad, pad, getWidth() - pad, getHeight() - pad);
        canvas.drawArc(oval, START_ANGLE, SWEEP_ANGLE, false, track);
        if (progress > 0f) canvas.drawArc(oval, START_ANGLE, SWEEP_ANGLE * progress, false, arc);
    }
}