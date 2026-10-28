package org.openxray;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/** Магазин Киткоинов. Открывается кнопкой SHOP в игре. */
public class ShopDialog extends Dialog {

    private static final int AMBER = 0xFFD9A441;
    private static final int TEXT = 0xFFD2CDBB;
    private static final int MUTED = 0xFF80836F;

    private final File gameDir;
    private final float d;
    private TextView balance;
    private LinearLayout content;

    public ShopDialog(Context c, File gameDir) {
        super(c, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        this.gameDir = gameDir;
        this.d = c.getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onCreate(android.os.Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0xF0090B07));
            w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        setCanceledOnTouchOutside(false);

        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(14), dp(24), dp(10));

        // Шапка: название, баланс, закрыть
        LinearLayout head = new LinearLayout(getContext());
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("МАГАЗИН", 18, AMBER, true);
        title.setLetterSpacing(0.14f);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout chip = new LinearLayout(getContext());
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(10), dp(5), dp(14), dp(5));
        chip.setBackground(rounded(0xFF1B1F13, 0xFF4C5434, 20));
        ImageView coin = new ImageView(getContext());
        coin.setImageResource(R.drawable.ic_kitcoin);
        chip.addView(coin, new LinearLayout.LayoutParams(dp(24), dp(24)));
        balance = text("", 16, AMBER, true);
        balance.setPadding(dp(8), 0, 0, 0);
        chip.addView(balance);
        head.addView(chip);

        ImageView close = new ImageView(getContext());
        close.setImageResource(R.drawable.ic_close);
        close.setColorFilter(TEXT);
        close.setPadding(dp(10), dp(10), dp(10), dp(10));
        close.setBackgroundResource(R.drawable.round_close);
        close.setOnClickListener(v -> dismiss());
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(dp(40), dp(40));
        cl.setMarginStart(dp(12));
        head.addView(close, cl);
        root.addView(head);

        TextView hint = text("Купленное хранится в приложении и появится в инвентаре при создании новой игры.",
                11, MUTED, false);
        hint.setPadding(0, dp(4), 0, dp(8));
        root.addView(hint);

        ScrollView sv = new ScrollView(getContext());
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        sv.addView(content);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        refresh();
    }

    private void refresh() {
        balance.setText(String.valueOf(KitStore.getCoins(getContext())));
        content.removeAllViews();
        for (int cat = 0; cat < KitStore.CAT_TITLES.length; cat++) {
            TextView h = text(KitStore.CAT_TITLES[cat], 12, AMBER, true);
            h.setLetterSpacing(0.16f);
            h.setPadding(0, dp(12), 0, dp(6));
            content.addView(h);

            GridLayout grid = new GridLayout(getContext());
            grid.setColumnCount(4);
            for (KitStore.Item it : KitStore.CATALOG) {
                if (it.cat != cat) continue;
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                        GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f));
                lp.width = 0;
                lp.setMargins(dp(4), dp(4), dp(4), dp(4));
                grid.addView(card(it), lp);
            }
            content.addView(grid, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private View card(KitStore.Item it) {
        int owned = KitStore.getOwned(getContext(), it);
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setBackground(rounded(0xFF161A11, owned > 0 ? AMBER : 0xFF3A4029, 14));

        ImageView img = new ImageView(getContext());
        img.setImageDrawable(icon(it.icon));
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        card.addView(img, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(78)));

        TextView name = text(it.title, 12, TEXT, true);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(4), 0, dp(4));
        card.addView(name);

        LinearLayout buy = new LinearLayout(getContext());
        buy.setGravity(Gravity.CENTER);
        buy.setPadding(dp(8), dp(6), dp(8), dp(6));
        boolean sold = !it.stack && owned > 0;
        buy.setBackground(rounded(sold ? 0xFF22271A : 0xFF4B4222, sold ? 0xFF4C5434 : AMBER, 18));
        if (sold) {
            buy.addView(text("КУПЛЕНО", 12, MUTED, true));
        } else {
            ImageView c = new ImageView(getContext());
            c.setImageResource(R.drawable.ic_kitcoin);
            buy.addView(c, new LinearLayout.LayoutParams(dp(18), dp(18)));
            TextView price = text(String.valueOf(it.price), 13, AMBER, true);
            price.setPadding(dp(6), 0, 0, 0);
            buy.addView(price);
            if (it.stack && owned > 0) {
                TextView cnt = text("  x" + owned, 11, MUTED, false);
                buy.addView(cnt);
            }
            buy.setOnClickListener(v -> onBuy(it));
        }
        card.addView(buy, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private void onBuy(KitStore.Item it) {
        int r = KitStore.buy(getContext(), it);
        if (r == KitStore.BUY_OK) {
            KitStore.writeItemsFile(getContext(), gameDir);
            Toast.makeText(getContext(), "Куплено: " + it.title, Toast.LENGTH_SHORT).show();
            refresh();
        } else if (r == KitStore.BUY_NO_COINS) {
            Toast.makeText(getContext(), "Не хватает Киткоинов", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getContext(), "Уже куплено", Toast.LENGTH_SHORT).show();
        }
    }

    private BitmapDrawable icon(int res) {
        BitmapDrawable bd = (BitmapDrawable) getContext().getResources().getDrawable(res, null);
        bd.setFilterBitmap(false); // пиксельные иконки без размытия
        return bd;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(getContext());
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(dp(1), stroke);
        return g;
    }

    private int dp(int v) { return Math.round(v * d); }
}
