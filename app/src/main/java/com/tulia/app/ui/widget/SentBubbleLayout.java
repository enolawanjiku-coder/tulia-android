package com.tulia.app.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Shader;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tulia.app.R;

public class SentBubbleLayout extends BubbleLayout {

    private final Matrix shaderMatrix = new Matrix();
    private final int[] screenLocation = new int[2];

    private LinearGradient shader;
    private int shaderHeight;

    public SentBubbleLayout(@NonNull Context context) {
        this(context, null);
    }

    public SentBubbleLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SentBubbleLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void rebuildRadii() {
        float big = dp(20);
        float small = dp(6);
        radii = new float[]{
                big, big,
                big, big,
                tail ? small : big, tail ? small : big,
                big, big
        };
    }

    private void ensureShader() {
        int height = getResources().getDisplayMetrics().heightPixels;
        if (shader != null && shaderHeight == height) {
            return;
        }
        shaderHeight = height;
        shader = new LinearGradient(
                0f, 0f, 0f, height,
                getContext().getColor(R.color.tulia_voice_start),
                getContext().getColor(R.color.tulia_voice_end),
                Shader.TileMode.CLAMP);
        fillPaint.setShader(shader);
    }

    @Override
    protected void drawFill(@NonNull Canvas canvas) {
        ensureShader();
        getLocationOnScreen(screenLocation);
        shaderMatrix.setTranslate(0f, -screenLocation[1]);
        shader.setLocalMatrix(shaderMatrix);
        canvas.drawPath(path, fillPaint);
    }
}
