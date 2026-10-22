package com.tulia.app.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tulia.app.R;

public class ReceivedBubbleLayout extends BubbleLayout {

    public ReceivedBubbleLayout(@NonNull Context context) {
        this(context, null);
    }

    public ReceivedBubbleLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ReceivedBubbleLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void init() {
        super.init();
        fillPaint.setColor(getContext().getColor(R.color.tulia_surface));
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(2));
        strokePaint.setColor(getContext().getColor(R.color.tulia_blush_line));
    }

    @Override
    protected void rebuildRadii() {
        float big = dp(20);
        float small = dp(6);
        radii = new float[]{
                big, big,
                big, big,
                big, big,
                tail ? small : big, tail ? small : big
        };
    }

    @Override
    protected void drawFill(@NonNull Canvas canvas) {
        canvas.drawPath(path, fillPaint);
        canvas.save();
        canvas.clipPath(path);
        canvas.drawPath(path, strokePaint);
        canvas.restore();
    }
}
