package dev.godlesscherry.tvstat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** A thin rounded progress track. Drawn, not typed — no block characters. */
public class BarView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float fraction;
    private int colour = 0xFF4C8DFF;
    private int track = 0xFF23272D;

    public BarView(Context context) {
        super(context);
    }

    public void set(float fraction, int colour) {
        this.fraction = Math.max(0f, Math.min(1f, fraction));
        this.colour = colour;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float h = getHeight();
        float r = h / 2f;
        paint.setColor(track);
        canvas.drawRoundRect(0, 0, getWidth(), h, r, r, paint);
        if (fraction > 0f) {
            float w = Math.max(h, getWidth() * fraction);
            paint.setColor(colour);
            canvas.drawRoundRect(0, 0, w, h, r, r, paint);
        }
    }
}
