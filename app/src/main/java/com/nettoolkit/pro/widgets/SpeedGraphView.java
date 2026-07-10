package com.nettoolkit.pro.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.LinkedList;

/**
 * نمودار خطی ساده و سبک برای نمایش سرعت دانلود/آپلود زنده، بدون کتابخانه‌ی جانبی.
 * یک پنجره‌ی چرخشی (Rolling Window) از آخرین N مقدار را رسم می‌کند.
 */
public class SpeedGraphView extends View {

    private static final int MAX_POINTS = 40;
    private final LinkedList<Float> downValues = new LinkedList<>();
    private final LinkedList<Float> upValues = new LinkedList<>();

    private final Paint downPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint upPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float maxValue = 100f; // حداکثر مقیاس فعلی نمودار (پویا)

    public SpeedGraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        downPaint.setColor(Color.parseColor("#00E5FF"));
        downPaint.setStyle(Paint.Style.STROKE);
        downPaint.setStrokeWidth(5f);
        downPaint.setStrokeCap(Paint.Cap.ROUND);

        upPaint.setColor(Color.parseColor("#8B5CF6"));
        upPaint.setStyle(Paint.Style.STROKE);
        upPaint.setStrokeWidth(5f);
        upPaint.setStrokeCap(Paint.Cap.ROUND);

        gridPaint.setColor(Color.parseColor("#232D42"));
        gridPaint.setStrokeWidth(1f);

        fillPaint.setStyle(Paint.Style.FILL);
    }

    public void pushSample(float downKbps, float upKbps) {
        downValues.add(downKbps);
        upValues.add(upKbps);
        if (downValues.size() > MAX_POINTS) downValues.removeFirst();
        if (upValues.size() > MAX_POINTS) upValues.removeFirst();

        float localMax = 10f;
        for (float v : downValues) localMax = Math.max(localMax, v);
        for (float v : upValues) localMax = Math.max(localMax, v);
        maxValue = localMax * 1.2f;

        invalidate();
    }

    public void clear() {
        downValues.clear();
        upValues.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();

        // خطوط شبکه افقی
        for (int i = 1; i < 4; i++) {
            float y = h * i / 4f;
            canvas.drawLine(0, y, w, y, gridPaint);
        }

        drawSeries(canvas, downValues, downPaint, w, h);
        drawSeries(canvas, upValues, upPaint, w, h);
    }

    private void drawSeries(Canvas canvas, LinkedList<Float> values, Paint paint, int w, int h) {
        if (values.size() < 2) return;

        Path path = new Path();
        float stepX = (float) w / (MAX_POINTS - 1);
        int startIndex = MAX_POINTS - values.size();

        boolean first = true;
        for (int i = 0; i < values.size(); i++) {
            float x = (startIndex + i) * stepX;
            float ratio = values.get(i) / maxValue;
            float y = h - (ratio * h * 0.9f) - (h * 0.05f);
            if (first) {
                path.moveTo(x, y);
                first = false;
            } else {
                path.lineTo(x, y);
            }
        }
        canvas.drawPath(path, paint);

        // گرادیانت زیر خط برای جلوه‌ی خفن‌تر
        Path fillPath = new Path(path);
        fillPath.lineTo((startIndex + values.size() - 1) * stepX, h);
        fillPath.lineTo(startIndex * stepX, h);
        fillPath.close();
        fillPaint.setShader(new LinearGradient(0, 0, 0, h,
                (paint.getColor() & 0x00FFFFFF) | 0x33000000,
                Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawPath(fillPath, fillPaint);
    }
}
