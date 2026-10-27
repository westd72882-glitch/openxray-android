package org.openxray;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * Слой с круглыми кнопками. Хранит положение каждой кнопки долей экрана,
 * поэтому раскладка переживает поворот и разные размеры экрана.
 */
public class MenuButtonsLayout extends ViewGroup {

    public interface EditModeListener {
        void onEditModeChanged(boolean editing);
    }

    private static final float PLAY_SIZE = 0.24f;   // диаметр от высоты слоя
    private static final float SIDE_SIZE = 0.17f;

    private final List<RoundMenuButton> buttons = new ArrayList<>();
    private SharedPreferences prefs;
    private EditModeListener listener;
    private boolean editMode;
    private float scale = 1f;
    private final float density;

    public MenuButtonsLayout(Context c) { this(c, null); }

    public MenuButtonsLayout(Context c, AttributeSet a) {
        super(c, a);
        density = c.getResources().getDisplayMetrics().density;
        setClipChildren(false);
    }

    public void setPrefs(SharedPreferences p) { prefs = p; }

    public void setEditModeListener(EditModeListener l) { listener = l; }

    public boolean isEditMode() { return editMode; }

    public void setScale(float s) {
        scale = Math.max(0.6f, Math.min(1.5f, s));
        requestLayout();
    }

    public RoundMenuButton addMenuButton(String key, int icon, String label, boolean primary,
                                         float defFx, float defFy, OnClickListener click) {
        RoundMenuButton b = new RoundMenuButton(getContext(), key, icon, label, primary, defFx, defFy);
        if (prefs != null) {
            b.fx = prefs.getFloat("menu_pos_" + key + "_x", defFx);
            b.fy = prefs.getFloat("menu_pos_" + key + "_y", defFy);
        }
        b.setOnClickListener(click);
        buttons.add(b);
        addView(b);
        return b;
    }

    public void setEditMode(boolean on) {
        if (editMode == on) return;
        editMode = on;
        for (RoundMenuButton b : buttons) b.setEditMode(on);
        if (!on) savePositions();
        if (listener != null) listener.onEditModeChanged(on);
    }

    public void resetPositions() {
        SharedPreferences.Editor ed = prefs != null ? prefs.edit() : null;
        for (RoundMenuButton b : buttons) {
            b.fx = b.defaultFx;
            b.fy = b.defaultFy;
            if (ed != null) ed.remove("menu_pos_" + b.key + "_x").remove("menu_pos_" + b.key + "_y");
        }
        if (ed != null) ed.apply();
        requestLayout();
    }

    public void savePositions() {
        if (prefs == null) return;
        SharedPreferences.Editor ed = prefs.edit();
        for (RoundMenuButton b : buttons) {
            ed.putFloat("menu_pos_" + b.key + "_x", b.fx);
            ed.putFloat("menu_pos_" + b.key + "_y", b.fy);
        }
        ed.apply();
    }

    /** Вызывается кнопкой во время перетаскивания. */
    public void dragButton(RoundMenuButton b, float newFx, float newFy) {
        b.fx = newFx;
        b.fy = newFy;
        clampInside(b);
        requestLayout();
    }

    private void clampInside(RoundMenuButton b) {
        int pw = getWidth();
        int ph = getHeight();
        if (pw <= 0 || ph <= 0) return;
        float margin = 8 * density;
        float halfW = b.desiredWidth() / 2f;
        float topOff = b.circleCenterY();
        float botOff = b.desiredHeight() - b.circleCenterY();
        float minX = (halfW + margin) / pw;
        float maxX = 1f - minX;
        float minY = (topOff + margin) / ph;
        float maxY = 1f - (botOff + margin) / ph;
        b.fx = Math.max(minX, Math.min(maxX, b.fx));
        b.fy = Math.max(minY, Math.min(maxY, b.fy));
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int h = MeasureSpec.getSize(hSpec);
        setMeasuredDimension(w, h);
        for (RoundMenuButton b : buttons) {
            float d = h * (b.primary ? PLAY_SIZE : SIDE_SIZE) * scale;
            d = Math.max(56 * density, Math.min(d, 150 * density));
            b.setDiameter(Math.round(d));
            b.measure(MeasureSpec.makeMeasureSpec(b.desiredWidth(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(b.desiredHeight(), MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int bm) {
        int pw = r - l;
        int ph = bm - t;
        for (RoundMenuButton b : buttons) {
            clampInside(b);
            int left = Math.round(b.fx * pw - b.circleCenterX());
            int top = Math.round(b.fy * ph - b.circleCenterY());
            b.layout(left, top, left + b.getMeasuredWidth(), top + b.getMeasuredHeight());
        }
    }
}
