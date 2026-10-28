package org.openxray;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.LinearLayout;

/**
 * Вертикальный список кнопок главного меню (стиль лаунчера Anomaly).
 * Старые методы перетаскивания оставлены пустыми для совместимости.
 */
public class MenuButtonsLayout extends LinearLayout {

    public interface EditModeListener {
        void onEditModeChanged(boolean editing);
    }

    private static final int PLAY_HEIGHT_DP = 54;
    private static final int SIDE_HEIGHT_DP = 46;
    private static final int GAP_DP = 8;

    private final float density;
    private float scale = 1f;

    public MenuButtonsLayout(Context c) { this(c, null); }

    public MenuButtonsLayout(Context c, AttributeSet a) {
        super(c, a);
        density = c.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
    }

    public void setPrefs(SharedPreferences p) { /* положение кнопок больше не настраивается */ }

    public void setEditModeListener(EditModeListener l) { }

    public boolean isEditMode() { return false; }

    public void setEditMode(boolean on) { }

    public void resetPositions() { }

    public void savePositions() { }

    public void setScale(float s) {
        scale = Math.max(0.8f, Math.min(1.3f, s));
        for (int i = 0; i < getChildCount(); i++) {
            RoundMenuButton b = (RoundMenuButton) getChildAt(i);
            b.getLayoutParams().height = heightFor(b.primary);
        }
        requestLayout();
    }

    private int heightFor(boolean primary) {
        return Math.round((primary ? PLAY_HEIGHT_DP : SIDE_HEIGHT_DP) * density * scale);
    }

    public RoundMenuButton addMenuButton(String key, int icon, String label, boolean primary,
                                         float defFx, float defFy, OnClickListener click) {
        RoundMenuButton b = new RoundMenuButton(getContext(), key, icon, label, primary);
        b.setOnClickListener(click);
        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightFor(primary));
        if (getChildCount() > 0) lp.topMargin = Math.round(GAP_DP * density);
        addView(b, lp);
        return b;
    }
}
