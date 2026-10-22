package com.tulia.app.ui.widget;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class BubbleLayout extends FrameLayout {

    protected final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected final Path path = new Path();
    protected final RectF rect = new RectF();
    protected final float density;

    protected float[] radii;
    protected boolean tail;

    private float maxWidthFraction = 0.78f;

    public BubbleLayout(@NonNull Context context) {
        this(context, null);
    }

    public BubbleLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BubbleLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        setClipToPadding(false);
        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        init();
    }

    protected void init() {
        rebuildRadii();
    }

    protected float dp(float value) {
        return value * density;
    }

    public void setTail(boolean isLastInGroup) {
        if (tail == isLastInGroup) {
            return;
        }
        tail = isLastInGroup;
        rebuildRadii();
        rebuildPath();
        invalidate();
    }

    public void setMaxWidthFraction(float fraction) {
        maxWidthFraction = fraction;
        requestLayout();
    }

    protected abstract void rebuildRadii();

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int max = (int) (getResources().getDisplayMetrics().widthPixels * maxWidthFraction);
        int mode = MeasureSpec.getMode(widthMeasureSpec);
        int size = MeasureSpec.getSize(widthMeasureSpec);
        if (mode == MeasureSpec.UNSPECIFIED) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST);
        } else if (size > max) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuildPath();
    }

    protected void rebuildPath() {
        if (radii == null || getWidth() == 0 || getHeight() == 0) {
            return;
        }
        rect.set(0f, 0f, getWidth(), getHeight());
        path.reset();
        path.addRoundRect(rect, radii, Path.Direction.CW);
        invalidate();
    }

    protected void drawFill(@NonNull android.graphics.Canvas canvas) {
        canvas.drawPath(path, fillPaint);
    }

    @Override
    protected void onDraw(@NonNull android.graphics.Canvas canvas) {
        super.onDraw(canvas);
        drawFill(canvas);
    }
}
