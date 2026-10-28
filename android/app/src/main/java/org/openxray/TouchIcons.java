package org.openxray;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import androidx.core.graphics.PathParser;

import java.util.HashMap;
import java.util.Map;

/**
 * Векторные иконки сенсорных кнопок. Каждая иконка — SVG-путь в сетке 24x24
 * (линейный стиль, скруглённые концы), поэтому чёткая при любом размере экрана.
 */
public final class TouchIcons {

    private static final Map<String, String> SVG = new HashMap<>();
    private static final Map<String, Path> CACHE = new HashMap<>();

    private static final String FIRE = "M18 12a6 6 0 1 1-12 0a6 6 0 0 1 12 0z M12 2v4 M12 18v4 M2 12h4 M18 12h4 M12 12h0.01";
    private static final String GRENADE = "M18 14.5a6 6 0 1 1-12 0a6 6 0 0 1 12 0z M10 8.5V5h4v3.5 M14 5l3.5-2 M9 14.5h6";

    static {
        SVG.put("lmb_main", FIRE);
        SVG.put("lmb_left", FIRE);
        // прицел: глаз
        SVG.put("rmb_main", "M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z M15 12a3 3 0 1 1-6 0a3 3 0 0 1 6 0z");
        // перезарядка
        SVG.put("r", "M20 12a8 8 0 1 1-2.5-5.8 M20 4v5h-5");
        // использовать: курсор-касание с лучами
        SVG.put("f", "M9 9l11 4-5 2-2 5z M9 3.5v2 M3.5 9h2 M5.1 5.1l1.4 1.4 M13 5.1l-1.4 1.4");
        // тип патронов: два патрона
        SVG.put("ammo", "M5.5 21v-9l2.5-4 2.5 4v9z M13.5 21v-9l2.5-4 2.5 4v9z M4 21h8 M12 21h8");
        // подствольник: труба с вылетающей гранатой
        SVG.put("gl", "M2 10h11v4H2z M13 12h4 M17 8.5l4 3.5-4 3.5 M6 14v5");
        SVG.put("jump", "M12 20V5 M6 11l6-6 6 6");
        SVG.put("crouch", "M12 4v15 M6 13l6 6 6-6");
        // шаг: два следа
        SVG.put("shift", "M13 4.5a1.8 1.8 0 1 1-3.6 0a1.8 1.8 0 0 1 3.6 0z M11 8l-1 5 3 2 1 5 M10 13l-3 1 M11 8l3 2 2.5 3 M10 13l-2.5 7");
        SVG.put("sprint", "M4 5l7 7-7 7 M12 5l7 7-7 7");
        // наклоны: стрелки-дуги влево/вправо
        SVG.put("q", "M9 3l6 12 M20 19H5 M9 15l-4 4 4 4");
        SVG.put("e", "M15 3l-6 12 M4 19h15 M15 15l4 4-4 4");
        SVG.put("medkit", "M3.5 8h17v12h-17z M9 8V5h6v3 M12 11.5v5 M9.5 14h5");
        SVG.put("bandage", "M4.5 16.5l12-12a2.8 2.8 0 0 1 4 4l-12 12a2.8 2.8 0 0 1-4-4z M9.5 8.5l6 6 M11 12.5h0.01 M12.5 11h0.01");
        // слоты оружия 1-6
        SVG.put("wpn_1", "M3.5 20.5l5-5 M8.5 15.5L19 3.5c.5 6-3 11-10.5 12z");
        SVG.put("wpn_2", "M3 7h16v4.5h-8l-1.5 8H6l1.5-8H3z M15 11.5v2.5");
        SVG.put("wpn_3", "M2 11h13l2-2.5h5V13h-5l-1 2H11.5l-1 5.5H7.5L9 15H2z M12 15v-2");
        SVG.put("wpn_4", GRENADE);
        SVG.put("wpn_5", "M9.5 15.5a3.5 3.5 0 1 1-7 0a3.5 3.5 0 0 1 7 0z M21.5 15.5a3.5 3.5 0 1 1-7 0a3.5 3.5 0 0 1 7 0z M9.5 14h5 M5 12V6h3.5v4 M19 12V6h-3.5v4");
        SVG.put("wpn_6", "M13 2L4.5 13.5H11l-1 8.5L19.5 10.5H13z");
        SVG.put("shop", "M5 8h14l-1 12H6z M9 8V6.5a3 3 0 0 1 6 0V8 M12 11.5v5 M9.5 14h5");
        SVG.put("esc", "M4 7h16 M4 12h16 M4 17h16");
        SVG.put("inv", "M6 21V10a6 6 0 0 1 12 0v11z M9 21v-5h6v5 M9 5V3.5h6V5 M6 12H4v6h2 M18 12h2v6h-2");
        SVG.put("pda", "M7.5 2.5h9a1 1 0 0 1 1 1v17a1 1 0 0 1-1 1h-9a1 1 0 0 1-1-1v-17a1 1 0 0 1 1-1z M9 6h6v6H9z M10 17h4");
        SVG.put("torch", "M8.5 3h7v4l-1.5 3.5V21h-4V10.5L8.5 7z M8.5 7h7 M12 13v3");
        SVG.put("detector", "M21 12a9 9 0 1 1-18 0a9 9 0 0 1 18 0z M12 12l6-4.5 M12 12h0.01 M8 9h0.01 M15 16h0.01");
        SVG.put("qsave", "M5 3h11l4 4v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z M8 3v5h7V3 M8 21v-7h8v7");
        SVG.put("qload", "M12 16V4 M7 9l5-5 5 5 M4 15v5h16v-5");
    }

    private TouchIcons() {}

    public static boolean has(String id) {
        return SVG.containsKey(id);
    }

    /** Рисует иконку по центру квадрата со стороной size. paint — штриховая кисть. */
    public static void draw(Canvas canvas, String id, float cx, float cy, float size, Paint paint) {
        String data = SVG.get(id);
        if (data == null) return;
        Path p = CACHE.get(id);
        if (p == null) {
            p = PathParser.createPathFromPathData(data);
            if (p == null) return;
            CACHE.put(id, p);
        }
        float k = size / 24f;
        canvas.save();
        canvas.translate(cx - size / 2f, cy - size / 2f);
        canvas.scale(k, k);
        paint.setStrokeWidth(1.9f); // в единицах сетки 24x24, масштабируется вместе с иконкой
        canvas.drawPath(p, paint);
        canvas.restore();
    }
}
