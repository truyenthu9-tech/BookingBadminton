package com.example.myapplication.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.example.myapplication.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Biểu đồ cột đơn giản vẽ bằng Canvas (không cần thư viện ngoài).
 * Dùng cho Dashboard: doanh thu theo tuần/tháng/năm và tỉ lệ đặt thành công theo sân.
 */
public class BarChartView extends View {

    public static class Bar {
        public final String label;
        public final float value;
        public final String valueText;
        public final int color;

        public Bar(String label, float value, String valueText, int color) {
            this.label = label;
            this.value = value;
            this.valueText = valueText;
            this.color = color;
        }
    }

    private final List<Bar> bars = new ArrayList<>();
    private float maxValue = 1f;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public BarChartView(Context context) {
        this(context, null);
    }

    public BarChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        valuePaint.setColor(ContextCompat.getColor(context, R.color.text_primary));
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTextSize(sp(10));
        labelPaint.setColor(ContextCompat.getColor(context, R.color.text_secondary));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(sp(10));
        emptyPaint.setColor(ContextCompat.getColor(context, R.color.text_secondary));
        emptyPaint.setTextAlign(Paint.Align.CENTER);
        emptyPaint.setTextSize(sp(13));
    }

    public void setBars(List<Bar> newBars) {
        bars.clear();
        if (newBars != null) bars.addAll(newBars);
        maxValue = 1f;
        for (Bar b : bars) maxValue = Math.max(maxValue, b.value);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float leftPad = dp(8), rightPad = dp(8), topPad = dp(26), bottomPad = dp(34);
        float chartW = getWidth() - leftPad - rightPad;
        float chartH = getHeight() - topPad - bottomPad;
        float baseline = topPad + chartH;

        if (bars.isEmpty()) {
            canvas.drawText("—", getWidth() / 2f, baseline - chartH / 2f, emptyPaint);
            return;
        }

        float slot = chartW / bars.size();
        float barW = Math.min(slot * 0.6f, dp(48));
        boolean showValues = bars.size() <= 8;

        for (int i = 0; i < bars.size(); i++) {
            Bar bar = bars.get(i);
            float centerX = leftPad + slot * i + slot / 2f;
            float h = maxValue > 0 ? (bar.value / maxValue) * chartH : 0;
            float left = centerX - barW / 2f;
            float top = baseline - h;

            barPaint.setColor(bar.color);
            RectF rect = new RectF(left, top, left + barW, baseline);
            float radius = Math.min(dp(6), barW / 2f);
            canvas.drawRoundRect(rect, radius, radius, barPaint);

            if (showValues) {
                canvas.drawText(bar.valueText, centerX, top - dp(4), valuePaint);
            }
            canvas.drawText(bar.label, centerX, baseline + dp(14), labelPaint);
        }
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private float sp(float v) {
        return v * getResources().getDisplayMetrics().scaledDensity;
    }
}
