package org.openxray;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

/**
 * Плоская кнопка главного меню в стиле лаунчера Anomaly: прямоугольник,
 * тонкая рамка и оранжевая полоса слева. Главная кнопка («ИГРАТЬ») залита акцентом.
 * Имя класса осталось от прежних круглых кнопок.
 */
public class RoundMenuButton extends View {

    public static final int ICON_PLAY = 0;
    public static final int ICON_GAMES = 1;
    public static final int ICON_SETTINGS = 2;

    private static final int ACCENT = 0xFFE8A33D;
    private static final int ACCENT_PRESSED = 0xFFB5701F;

    public final String key;
    public final int icon;
    public final boolean primary;

    private final String label;
    private final float density;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

    public RoundMenuButton(Context c, String key, int icon, String label, boolean primary) {
        super(c);
        this.key = key;
        this.icon = icon;
        this.label = label;
        this.primary = primary;
        density = c.getResources().getDisplayMetrics().density;

        setClickable(true);
        setFocusable(true);

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(density);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextSize((primary ? 17 : 14) * density);
        text.setLetterSpacing(0.12f);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float r = 3 * density;
        boolean down = isPressed();

        if (primary) {
            fill.setColor(down ? ACCENT_PRESSED : ACCENT);
            stroke.setColor(0x00000000);
            text.setColor(0xFF111111);
        } else {
            fill.setColor(down ? 0xFF2A2218 : 0xCC161616);
            stroke.setColor(down ? ACCENT : 0xFF3C3A36);
            text.setColor(down ? ACCENT : 0xFFDAD7CF);
        }
        canvas.drawRoundRect(0, 0, w, h, r, r, fill);
        canvas.drawRoundRect(density / 2f, density / 2f, w - density / 2f, h - density / 2f,
                r, r, stroke);

        if (!primary) {
            bar.setColor(ACCENT);
            canvas.drawRect(0, h * 0.2f, 3 * density, h * 0.8f, bar);
        }

        Paint.FontMetrics fm = text.getFontMetrics();
        float baseline = h / 2f - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(label, 20 * density, baseline, text);
    }
}
