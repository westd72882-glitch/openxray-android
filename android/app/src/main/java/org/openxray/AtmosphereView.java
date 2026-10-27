package org.openxray;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Тёмный «зоновский» фон лаунчера, нарисованный кодом (никаких картинок
 * и чужих ассетов): угольно-зелёный градиент, туман, виньетка,
 * знак радиации-водяной знак и тонкая «шумовая» сетка.
 */
public class AtmosphereView extends View {

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fog = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint vignette = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    public AtmosphereView(Context c) { super(c); init(); }
    public AtmosphereView(Context c, AttributeSet a) { super(c, a); init(); }
    public AtmosphereView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        mark.setStyle(Paint.Style.FILL);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(1f);
        line.setColor(0x0AFFFFFF);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        bg.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0xFF0B0E0A, 0xFF11150D, 0xFF080A07},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        // Туман: тёплое пятно снизу-слева (как закат над Зоной).
        fog.setShader(new RadialGradient(w * 0.25f, h * 1.05f, Math.max(w, h) * 0.7f,
                new int[]{0x55A8782A, 0x22503A18, 0x00000000},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        vignette.setShader(new RadialGradient(w * 0.5f, h * 0.5f, Math.max(w, h) * 0.75f,
                new int[]{0x00000000, 0x00000000, 0xCC000000},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        canvas.drawRect(0, 0, w, h, bg);
        canvas.drawRect(0, 0, w, h, fog);

        // Тонкая сетка, едва различимая.
        float step = 36f * getResources().getDisplayMetrics().density;
        for (float y = step; y < h; y += step) canvas.drawLine(0, y, w, y, line);

        // Знак радиации (водяной знак), три сектора.
        float r = Math.min(w, h) * 0.46f;
        float cx = w * 0.30f;
        float cy = h * 0.52f;
        mark.setColor(0x14D9A441);
        oval.set(cx - r, cy - r, cx + r, cy + r);
        for (int i = 0; i < 3; i++) {
            float start = -90f + i * 120f + 8f;
            canvas.drawArc(oval, start, 44f, true, mark);
        }
        mark.setColor(0xFF0B0E0A);
        canvas.drawCircle(cx, cy, r * 0.22f, mark);
        mark.setColor(0x14D9A441);
        canvas.drawCircle(cx, cy, r * 0.14f, mark);

        canvas.drawRect(0, 0, w, h, vignette);
    }
}
