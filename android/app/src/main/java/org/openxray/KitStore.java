package org.openxray;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * Киткоины и покупки. Всё хранится в данных приложения (SharedPreferences),
 * поэтому переживает перезапуск игры. Купленное попадает в инвентарь при
 * создании новой игры: файл gamedata/config/kit_items.ltx читает скрипт kit_shop.script.
 */
public final class KitStore {

    public static final int START_COINS = 999;
    public static final int CAT_ARMOR = 0, CAT_WEAPON = 1, CAT_AMMO = 2;
    public static final String[] CAT_TITLES = {"БРОНЯ", "ОРУЖИЕ", "ПАТРОНЫ И ПРОЧЕЕ"};

    public static final class Item {
        public final String id;       // ключ в prefs
        public final String section;  // секция предмета в игре (SoC)
        public final String title;
        public final int price;
        public final int icon;
        public final int cat;
        public final boolean stack;   // можно покупать много раз
        Item(String id, String section, String title, int price, int icon, int cat, boolean stack) {
            this.id = id; this.section = section; this.title = title;
            this.price = price; this.icon = icon; this.cat = cat; this.stack = stack;
        }
    }

    /** Каталог. Цены меняются здесь. Всё покупается без ограничений (stack = true). */
    public static final Item[] CATALOG = {
            new Item("specops", "specops_outfit", "Берилл-5М", 10, R.drawable.shop_specops, CAT_ARMOR, true),
            new Item("bandit", "stalker_outfit", "Куртка бандита", 5, R.drawable.shop_bandit, CAT_ARMOR, true),
            new Item("scientific", "scientific_outfit", "Научный костюм", 9, R.drawable.shop_scientific, CAT_ARMOR, true),
            new Item("military", "military_outfit", "Бронежилет военных", 8, R.drawable.shop_military, CAT_ARMOR, true),

            new Item("pm", "wpn_pm", "ПМ", 5, R.drawable.shop_wpn_pm, CAT_WEAPON, true),
            new Item("ak74u", "wpn_ak74u", "АКС-74У", 9, R.drawable.shop_wpn_ak74u, CAT_WEAPON, true),
            new Item("bm16", "wpn_bm16", "БМ-16", 8, R.drawable.shop_wpn_bm16, CAT_WEAPON, true),
            new Item("knife", "wpn_knife", "Нож", 5, R.drawable.shop_wpn_knife, CAT_WEAPON, true),

            new Item("ammo_9x18", "ammo_9x18_fmj", "Патроны 9x18", 5, R.drawable.shop_ammo_9x18, CAT_AMMO, true),
            new Item("ammo_545", "ammo_5.45x39_fmj", "Патроны 5.45x39", 6, R.drawable.shop_ammo_545, CAT_AMMO, true),
            new Item("ammo_12x70", "ammo_12x70_buck", "Картечь 12x70", 6, R.drawable.shop_ammo_12x70, CAT_AMMO, true),
            new Item("grenade", "grenade_f1", "Граната Ф-1", 7, R.drawable.shop_grenade_f1, CAT_AMMO, true),
    };

    public static final int BUY_OK = 0, BUY_NO_COINS = 1, BUY_OWNED = 2;

    private static final String PREFS = "kitcoin_store";

    private KitStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int getCoins(Context c) {
        SharedPreferences p = prefs(c);
        // coins2: один раз выдаём 999 монет всем, в том числе тем, у кого был старый баланс
        if (!p.contains("coins2")) {
            p.edit().putInt("coins2", START_COINS).apply();
            return START_COINS;
        }
        return p.getInt("coins2", START_COINS);
    }

    public static int getOwned(Context c, Item it) {
        return prefs(c).getInt("own_" + it.id, 0);
    }

    public static int buy(Context c, Item it) {
        int coins = getCoins(c);
        int owned = getOwned(c, it);
        if (coins < it.price) return BUY_NO_COINS;
        prefs(c).edit().putInt("coins2", coins - it.price).putInt("own_" + it.id, owned + 1).apply();
        return BUY_OK;
    }

    /** Пишет список купленного для скрипта игры. Только Тень Чернобыля (папка config). */
    public static void writeItemsFile(Context c, File gameDir) {
        if (gameDir == null) return;
        try {
            File cfg = new File(new File(gameDir, "gamedata"), "config");
            if (!cfg.exists() && !cfg.mkdirs()) return;
            StringBuilder sb = new StringBuilder("[items]\n");
            for (Item it : CATALOG) {
                int n = getOwned(c, it);
                if (n > 0) sb.append(it.section).append(" = ").append(n).append('\n');
            }
            try (Writer w = new OutputStreamWriter(new FileOutputStream(new File(cfg, "kit_items.ltx")),
                    StandardCharsets.UTF_8)) {
                w.write(sb.toString());
            }
        } catch (Exception e) {
            AppLog.e("KitStore", "Не удалось записать kit_items.ltx: " + e.getMessage(), e);
        }
    }
}
