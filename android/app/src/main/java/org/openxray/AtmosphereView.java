package org.openxray;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * Фон лаунчера: случайный скриншот Зоны на весь экран (с обрезкой по центру),
 * поверх него тёмное затемнение слева (под текст) и виньетка.
 */
public class AtmosphereView extends View {

    private static final int[] BACKGROUNDS = {
            R.drawable.bg_zone_1, R.drawable.bg_zone_2, R.drawable.bg_zone_3
    };

    private final Paint bmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint();
    private final Paint shade = new Paint();
    private final Paint vignette = new Paint();
    private final Matrix matrix = new Matrix();
    private Bitmap bitmap;
    private final int bgIndex = new Random().nextInt(BACKGROUNDS.length);

    public AtmosphereView(Context c) { super(c); init(); }
    public AtmosphereView(Context c, AttributeSet a) { super(c, a); init(); }
    public AtmosphereView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        fill.setColor(0xFF0B0E0A);
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            bitmap = BitmapFactory.decodeResource(getResources(), BACKGROUNDS[bgIndex], o);
        } catch (Throwable t) {
            bitmap = null; // без фона просто тёмный экран
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (bitmap != null && w > 0 && h > 0) {
            float s = Math.max(w / (float) bitmap.getWidth(), h / (float) bitmap.getHeight());
            matrix.reset();
            matrix.postScale(s, s);
            matrix.postTranslate((w - bitmap.getWidth() * s) / 2f, (h - bitmap.getHeight() * s) / 2f);
        }
        // Затемнение слева направо: текст читается, справа остаётся картинка.
        shade.setShader(new LinearGradient(0, 0, w, 0,
                new int[]{0xE6080A07, 0x99080A07, 0x33080A07},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        vignette.setShader(new RadialGradient(w * 0.5f, h * 0.5f, Math.max(w, h) * 0.75f,
                new int[]{0x00000000, 0x00000000, 0xAA000000},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        canvas.drawRect(0, 0, w, h, fill);
        if (bitmap != null) canvas.drawBitmap(bitmap, matrix, bmpPaint);
        canvas.drawRect(0, 0, w, h, shade);
        canvas.drawRect(0, 0, w, h, vignette);
    }
}
