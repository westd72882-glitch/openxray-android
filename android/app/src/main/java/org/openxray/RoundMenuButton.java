package org.openxray;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * Круглая кнопка главного меню. Иконка рисуется кодом.
 * В режиме редактирования кнопку можно перетащить пальцем; долгое нажатие
 * на любую кнопку включает этот режим.
 */
public class RoundMenuButton extends View {

    public static final int ICON_PLAY = 0;
    public static final int ICON_GAMES = 1;
    public static final int ICON_SETTINGS = 2;

    private static final int LONG_PRESS_MS = 550;

    public final String key;
    public final int icon;
    public final boolean primary;
    public final float defaultFx;
    public final float defaultFy;

    /** Положение центра круга, доля от ширины/высоты родителя. */
    public float fx;
    public float fy;

    private String label;
    private int diameter;
    private final float density;
    private final float glow;
    private final float labelH;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint holePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private boolean editMode;
    private boolean pressedLook;
    private boolean moved;
    private boolean longFired;
    private float downRawX, downRawY, startFx, startFy;
    private final int slop;

    private final Runnable longPress = new Runnable() {
        @Override public void run() {
            longFired = true;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            if (getParent() instanceof MenuButtonsLayout) {
                ((MenuButtonsLayout) getParent()).setEditMode(true);
            }
        }
    };

    public RoundMenuButton(Context c, String key, int icon, String label, boolean primary,
                           float defFx, float defFy) {
        super(c);
        this.key = key;
        this.icon = icon;
        this.label = label;
        this.primary = primary;
        this.defaultFx = defFx;
        this.defaultFy = defFy;
        this.fx = defFx;
        this.fy = defFy;
        density = c.getResources().getDisplayMetrics().density;
        glow = 10f * density;
        labelH = 24f * density;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        setClickable(true);

        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setLetterSpacing(0.08f);
        textPaint.setColor(0xFFD2CDBB);
        ringPaint.setStyle(Paint.Style.STROKE);
        innerRing.setStyle(Paint.Style.STROKE);
        iconPaint.setStyle(Paint.Style.FILL);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);
        holePaint.setStyle(Paint.Style.FILL);
        glowPaint.setStyle(Paint.Style.FILL);
    }

    public void setDiameter(int px) {
        if (px != diameter) {
            diameter = px;
            rebuildShaders();
            textPaint.setTextSize(Math.max(10f * density, Math.min(13f * density, diameter * 0.16f)));
        }
    }

    public int getDiameter() { return diameter; }

    public int desiredWidth() {
        float tw = textPaint.measureText(label);
        return (int) Math.ceil(Math.max(diameter + 2 * glow, tw + 12 * density));
    }

    public int desiredHeight() {
        return (int) Math.ceil(diameter + 2 * glow + labelH);
    }

    /** Смещение центра круга внутри самого view. */
    public float circleCenterX() { return desiredWidth() / 2f; }
    public float circleCenterY() { return glow + diameter / 2f; }

    public void setEditMode(boolean on) {
        editMode = on;
        invalidate();
    }

    private void rebuildShaders() {
        float r = diameter / 2f;
        int amber = 0xFFD9A441;
        int[] fillCols = primary
                ? new int[]{0xFF4B4222, 0xFF1B170A}
                : new int[]{0xFF343A28, 0xFF14170F};
        fillPaint.setShader(new RadialGradient(0, -r * 0.35f, r * 1.25f, fillCols, null, Shader.TileMode.CLAMP));
        int gc = primary ? 0x77D9A441 : 0x335E6648;
        float outer = r + glow;
        glowPaint.setShader(new RadialGradient(0, 0, outer,
                new int[]{gc, gc, (gc & 0x00FFFFFF)},
                new float[]{0f, r / outer, 1f}, Shader.TileMode.CLAMP));
        ringPaint.setStrokeWidth((primary ? 3.2f : 2.4f) * density);
        ringPaint.setColor(primary ? amber : 0xFF7A8260);
        innerRing.setStrokeWidth(1f * density);
        innerRing.setColor(primary ? 0x66D9A441 : 0x445E6648);
        iconPaint.setColor(primary ? amber : 0xFFCFC9B4);
        holePaint.setColor(primary ? 0xFF1B170A : 0xFF14170F);
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(resolveSize(desiredWidth(), w), resolveSize(desiredHeight(), h));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = glow + diameter / 2f;
        float r = diameter / 2f;
        float scale = pressedLook ? 0.94f : 1f;

        canvas.save();
        canvas.translate(cx, cy);
        canvas.scale(scale, scale);

        canvas.drawCircle(0, 0, r + glow, glowPaint);
        canvas.drawCircle(0, 0, r, fillPaint);

        if (editMode) {
            ringPaint.setPathEffect(new DashPathEffect(new float[]{9 * density, 6 * density}, 0));
            ringPaint.setColor(0xFFFFC24A);
        } else {
            ringPaint.setPathEffect(null);
            ringPaint.setColor(primary ? 0xFFD9A441 : 0xFF7A8260);
        }
        canvas.drawCircle(0, 0, r - ringPaint.getStrokeWidth() / 2f, ringPaint);
        canvas.drawCircle(0, 0, r - 7 * density, innerRing);

        drawIcon(canvas, r);
        canvas.restore();

        canvas.drawText(label, cx, glow + diameter + labelH * 0.68f, textPaint);
    }

    private void drawIcon(Canvas canvas, float r) {
        switch (icon) {
            case ICON_PLAY: {
                float s = r * 0.46f;
                path.reset();
                path.moveTo(-s * 0.55f, -s);
                path.lineTo(s * 0.95f, 0);
                path.lineTo(-s * 0.55f, s);
                path.close();
                iconPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                iconPaint.setStrokeWidth(r * 0.09f);
                canvas.drawPath(path, iconPaint);
                iconPaint.setStyle(Paint.Style.FILL);
                break;
            }
            case ICON_GAMES: {
                float s = r * 0.30f;
                float gap = r * 0.07f;
                float rad = s * 0.28f;
                for (int ix = 0; ix < 2; ix++) {
                    for (int iy = 0; iy < 2; iy++) {
                        float l = ix == 0 ? -s - gap : gap;
                        float t = iy == 0 ? -s - gap : gap;
                        rect.set(l, t, l + s, t + s);
                        canvas.drawRoundRect(rect, rad, rad, iconPaint);
                    }
                }
                break;
            }
            case ICON_SETTINGS: {
                float R = r * 0.50f;
                float tw = r * 0.20f;
                float th = r * 0.20f;
                for (int i = 0; i < 8; i++) {
                    canvas.save();
                    canvas.rotate(i * 45f);
                    rect.set(-tw / 2f, -R - th * 0.35f, tw / 2f, -R + th * 0.75f);
                    canvas.drawRoundRect(rect, tw * 0.2f, tw * 0.2f, iconPaint);
                    canvas.restore();
                }
                canvas.drawCircle(0, 0, R, iconPaint);
                canvas.drawCircle(0, 0, R * 0.42f, holePaint);
                break;
            }
            default:
                break;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        MenuButtonsLayout layer = getParent() instanceof MenuButtonsLayout
                ? (MenuButtonsLayout) getParent() : null;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = e.getRawX();
                downRawY = e.getRawY();
                startFx = fx;
                startFy = fy;
                moved = false;
                longFired = false;
                pressedLook = true;
                invalidate();
                if (layer != null && !layer.isEditMode()) {
                    postDelayed(longPress, LONG_PRESS_MS);
                }
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getRawX() - downRawX;
                float dy = e.getRawY() - downRawY;
                if (!moved && Math.hypot(dx, dy) > slop) {
                    moved = true;
                    removeCallbacks(longPress);
                }
                if (moved && layer != null && layer.isEditMode()) {
                    layer.dragButton(this, startFx + dx / Math.max(1, layer.getWidth()),
                            startFy + dy / Math.max(1, layer.getHeight()));
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                removeCallbacks(longPress);
                pressedLook = false;
                invalidate();
                if (layer != null && layer.isEditMode()) {
                    if (moved) layer.savePositions();
                } else if (!moved && !longFired) {
                    performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPress);
                pressedLook = false;
                invalidate();
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
