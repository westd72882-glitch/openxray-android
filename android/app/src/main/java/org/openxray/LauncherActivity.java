package org.openxray;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.util.DisplayMetrics;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import android.content.ClipData;
import android.content.ClipboardManager;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Locale;

/**
 * Главный экран. Тёмное меню в стиле Зоны: три круглые кнопки справа
 * (ИГРАТЬ, ВЫБОР ИГРЫ, НАСТРОЙКИ), положение которых можно менять.
 * «ИГРАТЬ» запускает уже настроенную конфигурацию и открывает главное меню
 * игры: сложность и новую игру игрок выбирает уже там.
 */
public class LauncherActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "openxray_launcher_prefs";
    private static final int PERMISSION_REQUEST_CODE = 2001;
    private static final int BROWSE_FOLDER_REQUEST_CODE = 2002;

    private static final String[] MODES = {"soc", "cs", "cop"};

    private SharedPreferences mPrefs;
    private String mGamePath = "";
    private String mGameMode = "soc";

    // Главный экран
    private MenuButtonsLayout mMenuLayer;
    private View mEditBar;
    private TextView mTextSelectedGame;
    private TextView mTextMainStatus;
    private View mDotStatus;

    // Панель «Выбор игры»
    private View mPanelGames;
    private EditText mEditGamePath;
    private TextView mTextPathStatus;

    // Панель «Настройки»
    private View mPanelSettings;
    private CheckBox mCheckNoIntro;
    private CheckBox mCheckNoSound;
    private CheckBox mCheckNoShadows;
    private CheckBox mCheckDLights;
    private CheckBox mCheckNoSun;
    private CheckBox mCheckFastDxt;
    private Spinner mSpinnerGraphicsPreset;
    private java.util.List<GraphicsPresetItem> mGraphicsPresetList = new java.util.ArrayList<>();
    private ArrayAdapter<GraphicsPresetItem> mGraphicsPresetAdapter;
    private Spinner mSpinnerResolution;
    private java.util.List<ResolutionItem> mResolutionList = new java.util.ArrayList<>();
    private ArrayAdapter<ResolutionItem> mResolutionAdapter;
    private Spinner mSpinnerRenderBackend;
    private TextView mTextRenderBackendStatus;
    private java.util.List<RenderBackendItem> mRenderBackendList = new java.util.ArrayList<>();
    private ArrayAdapter<RenderBackendItem> mRenderBackendAdapter;
    private EditText mEditCustomArgs;

    private TextView mLabelLookSens;
    private SeekBar mSeekLookSens;
    private TextView mLabelTouchOpacity;
    private SeekBar mSeekTouchOpacity;
    private TextView mLabelTouchScale;
    private SeekBar mSeekTouchScale;
    private TextView mLabelMenuScale;
    private SeekBar mSeekMenuScale;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        setContentView(R.layout.activity_launcher);

        mPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedPath = mPrefs.getString("game_path", null);
        AppLog.init(this, savedPath);
        AppLog.i("Launcher", "LauncherActivity onCreate started");

        mGamePath = savedPath != null ? savedPath : defaultGamePath();
        mGameMode = mPrefs.getString("game_mode", "soc");
        if (!"cs".equals(mGameMode) && !"cop".equals(mGameMode)) mGameMode = "soc";

        initViews();
        loadPreferences();
        checkAndRequestPermissions();
        checkGamePathStatus();
        offerCrashReportIfPending();
        AppLog.i("Launcher", "LauncherActivity onCreate finished");
    }

    private static String defaultGamePath() {
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "Stalker");
        try {
            if (!dir.exists()) dir.mkdirs();
        } catch (Exception ignored) {}
        return dir.getAbsolutePath();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    @Override
    public void onBackPressed() {
        if (mMenuLayer != null && mMenuLayer.isEditMode()) {
            mMenuLayer.setEditMode(false);
            return;
        }
        if (isPanelOpen()) {
            hidePanels();
            return;
        }
        super.onBackPressed();
    }

    private boolean isPanelOpen() {
        return mPanelGames.getVisibility() == View.VISIBLE
                || mPanelSettings.getVisibility() == View.VISIBLE;
    }

    private void hidePanels() {
        mPanelGames.setVisibility(View.GONE);
        mPanelSettings.setVisibility(View.GONE);
        savePreferences();
        hideKeyboard();
    }

    private void hideKeyboard() {
        try {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            View f = getCurrentFocus();
            if (imm != null && f != null) imm.hideSoftInputFromWindow(f.getWindowToken(), 0);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateKitcoins();
    }

    /** Показывает баланс Киткоинов (хранится в данных приложения, на старте 5). */
    private void updateKitcoins() {
        TextView t = findViewById(R.id.text_kitcoins);
        if (t != null) t.setText(String.valueOf(KitStore.getCoins(this)));
    }

    private void showGamesPanel() {
        mPanelSettings.setVisibility(View.GONE);
        mPanelGames.setVisibility(View.VISIBLE);
        checkGamePathStatus();
    }

    private void showSettingsPanel() {
        mPanelGames.setVisibility(View.GONE);
        mPanelSettings.setVisibility(View.VISIBLE);
    }

    private void initViews() {
        // ---- Главный экран ----
        mTextSelectedGame = findViewById(R.id.text_selected_game);
        mTextMainStatus = findViewById(R.id.text_main_status);
        mDotStatus = findViewById(R.id.dot_status);
        mEditBar = findViewById(R.id.edit_bar);

        mMenuLayer = findViewById(R.id.menu_layer);
        mMenuLayer.setPrefs(mPrefs);
        mMenuLayer.setScale(mPrefs.getInt("menu_scale", 100) / 100f);
        mMenuLayer.addMenuButton("play", RoundMenuButton.ICON_PLAY, "ИГРАТЬ", true,
                0.86f, 0.19f, v -> launchGame());
        mMenuLayer.addMenuButton("games", RoundMenuButton.ICON_GAMES, "ВЫБОР ИГРЫ", false,
                0.86f, 0.52f, v -> showGamesPanel());
        mMenuLayer.addMenuButton("settings", RoundMenuButton.ICON_SETTINGS, "НАСТРОЙКИ", false,
                0.86f, 0.80f, v -> showSettingsPanel());
        mMenuLayer.setEditModeListener(editing -> {
            mEditBar.setVisibility(editing ? View.VISIBLE : View.GONE);
            if (editing) {
                mPanelGames.setVisibility(View.GONE);
                mPanelSettings.setVisibility(View.GONE);
            }
        });
        findViewById(R.id.btn_edit_done).setOnClickListener(v -> mMenuLayer.setEditMode(false));
        findViewById(R.id.btn_edit_reset).setOnClickListener(v -> {
            mMenuLayer.resetPositions();
            Toast.makeText(this, "Расположение сброшено", Toast.LENGTH_SHORT).show();
        });

        // ---- Панель «Выбор игры» ----
        mPanelGames = findViewById(R.id.panel_games);
        mEditGamePath = findViewById(R.id.edit_game_path);
        mTextPathStatus = findViewById(R.id.text_path_status);
        mEditGamePath.setText(mGamePath);
        findViewById(R.id.btn_games_close).setOnClickListener(v -> hidePanels());
        findViewById(R.id.btn_browse_path).setOnClickListener(v -> openFolderPicker());
        findViewById(R.id.btn_check_path).setOnClickListener(v -> checkGamePathStatus());
        findViewById(R.id.card_soc).setOnClickListener(v -> selectGame("soc"));
        findViewById(R.id.card_cs).setOnClickListener(v -> selectGame("cs"));
        findViewById(R.id.card_cop).setOnClickListener(v -> selectGame("cop"));
        mEditGamePath.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void afterTextChanged(Editable s) {
                mGamePath = s.toString().trim();
                mPrefs.edit().putString("game_path", mGamePath).apply();
                checkGamePathStatus();
            }
        });

        // ---- Панель «Настройки» ----
        mPanelSettings = findViewById(R.id.panel_settings);
        findViewById(R.id.btn_settings_close).setOnClickListener(v -> hidePanels());

        mCheckNoIntro = findViewById(R.id.check_nointro);
        mCheckNoSound = findViewById(R.id.check_nosound);
        mCheckNoShadows = findViewById(R.id.check_noshadows);
        mCheckDLights = findViewById(R.id.check_dlights);
        mCheckNoSun = findViewById(R.id.check_nosun);
        mCheckFastDxt = findViewById(R.id.check_fastdxt);
        mSpinnerGraphicsPreset = findViewById(R.id.spinner_graphics_preset);
        setupGraphicsPresetSpinner();
        mSpinnerResolution = findViewById(R.id.spinner_resolution);
        setupResolutionSpinner();
        mSpinnerRenderBackend = findViewById(R.id.spinner_render_backend);
        mTextRenderBackendStatus = findViewById(R.id.text_render_backend_status);
        setupRenderBackendSpinner();
        mEditCustomArgs = findViewById(R.id.edit_custom_args);

        mLabelLookSens = findViewById(R.id.label_look_sens);
        mSeekLookSens = findViewById(R.id.seek_look_sens);
        mLabelTouchOpacity = findViewById(R.id.label_touch_opacity);
        mSeekTouchOpacity = findViewById(R.id.seek_touch_opacity);
        mLabelTouchScale = findViewById(R.id.label_touch_scale);
        mSeekTouchScale = findViewById(R.id.seek_touch_scale);
        mLabelMenuScale = findViewById(R.id.label_menu_scale);
        mSeekMenuScale = findViewById(R.id.seek_menu_scale);

        findViewById(R.id.btn_edit_touch_layout).setOnClickListener(v ->
                startActivity(new Intent(this, ControlLayoutEditorActivity.class)));
        findViewById(R.id.btn_edit_menu_layout).setOnClickListener(v -> {
            hidePanels();
            mMenuLayer.setEditMode(true);
        });
        findViewById(R.id.btn_reset_menu_layout).setOnClickListener(v -> {
            mMenuLayer.resetPositions();
            Toast.makeText(this, "Расположение сброшено", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btn_view_logs).setOnClickListener(v -> showLogsDialog());
        findViewById(R.id.btn_report_bug).setOnClickListener(v -> showBugTrackerDialog());
        findViewById(R.id.btn_support_dev).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://boosty.to/openxray")));
            } catch (Exception e) {
                Toast.makeText(this, "Не удалось открыть браузер", Toast.LENGTH_SHORT).show();
            }
        });

        mSeekLookSens.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float val = Math.max(5, progress) / 10.0f;
                mLabelLookSens.setText(String.format(Locale.US, "Чувствительность камеры: %.1fx", val));
            }
        });
        mSeekTouchOpacity.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = Math.max(20, progress);
                mLabelTouchOpacity.setText(String.format(Locale.US, "Прозрачность кнопок: %d%%", val));
            }
        });
        mSeekTouchScale.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = Math.max(70, progress);
                mLabelTouchScale.setText(String.format(Locale.US, "Размер кнопок в игре: %d%%", val));
            }
        });
        mSeekMenuScale.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = Math.max(60, progress);
                mLabelMenuScale.setText(String.format(Locale.US, "Размер круглых кнопок: %d%%", val));
                mMenuLayer.setScale(val / 100f);
            }
        });
    }

    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }

    private void loadPreferences() {
        mCheckNoIntro.setChecked(mPrefs.getBoolean("nointro", false));
        mCheckNoSound.setChecked(mPrefs.getBoolean("nosound", false));
        mCheckNoShadows.setChecked(mPrefs.getBoolean("noshadows", false));
        mCheckDLights.setChecked(mPrefs.getBoolean("dlights", true));
        mCheckNoSun.setChecked(mPrefs.getBoolean("nosun", false));
        mCheckFastDxt.setChecked(mPrefs.getBoolean("fastdxt", true));

        String savedPreset = mPrefs.getString("graphics_preset", "none");
        int presetIndex = 0;
        for (int i = 0; i < mGraphicsPresetList.size(); i++) {
            if (mGraphicsPresetList.get(i).id.equals(savedPreset)) {
                presetIndex = i;
                break;
            }
        }
        mSpinnerGraphicsPreset.setSelection(presetIndex);

        if (!mPrefs.getBoolean("custom_args_v2_initialized", false)) {
            String customArgs = mPrefs.getString("custom_args", "");
            if (customArgs.isEmpty()) {
                customArgs = "-xclsx";
            } else if (!customArgs.contains("-xclsx")) {
                customArgs = (customArgs + " -xclsx").trim();
            }
            mEditCustomArgs.setText(customArgs);
            mPrefs.edit().putBoolean("custom_args_v2_initialized", true).apply();
        } else {
            mEditCustomArgs.setText(mPrefs.getString("custom_args", "-xclsx"));
        }

        int lookSens = mPrefs.getInt("look_sens", 10);
        mSeekLookSens.setProgress(lookSens);
        mLabelLookSens.setText(String.format(Locale.US, "Чувствительность камеры: %.1fx", lookSens / 10.0f));

        int opacity = mPrefs.getInt("touch_opacity", 60);
        mSeekTouchOpacity.setProgress(opacity);
        mLabelTouchOpacity.setText(String.format(Locale.US, "Прозрачность кнопок: %d%%", opacity));

        int scale = mPrefs.getInt("touch_scale", 100);
        mSeekTouchScale.setProgress(scale);
        mLabelTouchScale.setText(String.format(Locale.US, "Размер кнопок в игре: %d%%", scale));

        int menuScale = mPrefs.getInt("menu_scale", 100);
        mSeekMenuScale.setProgress(menuScale);
        mLabelMenuScale.setText(String.format(Locale.US, "Размер круглых кнопок: %d%%", menuScale));

        int savedW = mPrefs.getInt("res_width", 0);
        int savedH = mPrefs.getInt("res_height", 0);
        int selectedIndex = 0;
        for (int i = 0; i < mResolutionList.size(); i++) {
            ResolutionItem item = mResolutionList.get(i);
            if (item.width == savedW && item.height == savedH) {
                selectedIndex = i;
                break;
            }
        }
        mSpinnerResolution.setSelection(selectedIndex);

        String savedBackend = mPrefs.getString("render_backend", "native");
        int backendIndex = 0;
        for (int i = 0; i < mRenderBackendList.size(); i++) {
            if (mRenderBackendList.get(i).id.equals(savedBackend)) {
                backendIndex = i;
                break;
            }
        }
        mSpinnerRenderBackend.setSelection(backendIndex);
        updateRenderBackendStatus();
    }

    private void savePreferences() {
        mPrefs.edit()
            .putString("game_path", mGamePath)
            .putString("game_mode", mGameMode)
            .putBoolean("nointro", mCheckNoIntro.isChecked())
            .putBoolean("nosound", mCheckNoSound.isChecked())
            .putBoolean("noshadows", mCheckNoShadows.isChecked())
            .putBoolean("dlights", mCheckDLights.isChecked())
            .putBoolean("nosun", mCheckNoSun.isChecked())
            .putBoolean("fastdxt", mCheckFastDxt.isChecked())
            .putString("custom_args", mEditCustomArgs.getText().toString().trim())
            .putInt("look_sens", Math.max(5, mSeekLookSens.getProgress()))
            .putInt("touch_opacity", Math.max(20, mSeekTouchOpacity.getProgress()))
            .putInt("touch_scale", Math.max(70, mSeekTouchScale.getProgress()))
            .putInt("menu_scale", Math.max(60, mSeekMenuScale.getProgress()))
            .apply();

        if (mSpinnerResolution.getSelectedItem() instanceof ResolutionItem) {
            ResolutionItem item = (ResolutionItem) mSpinnerResolution.getSelectedItem();
            mPrefs.edit()
                .putInt("res_width", item.width)
                .putInt("res_height", item.height)
                .apply();
        }
        if (mSpinnerRenderBackend.getSelectedItem() instanceof RenderBackendItem) {
            RenderBackendItem item = (RenderBackendItem) mSpinnerRenderBackend.getSelectedItem();
            mPrefs.edit().putString("render_backend", item.id).apply();
        }
        if (mSpinnerGraphicsPreset.getSelectedItem() instanceof GraphicsPresetItem) {
            GraphicsPresetItem item = (GraphicsPresetItem) mSpinnerGraphicsPreset.getSelectedItem();
            mPrefs.edit().putString("graphics_preset", item.id).apply();
        }
    }

    private String selectedGameMode() {
        return mGameMode;
    }

    private static String gameTitle(String mode) {
        if ("cs".equals(mode)) return "Чистое небо";
        if ("cop".equals(mode)) return "Зов Припяти";
        return "Тень Чернобыля";
    }

    private int countArchives(String mode) {
        if (mGamePath == null || mGamePath.isEmpty()) return 0;
        File[] dbFiles = AppLog.collectDatabaseArchives(new File(mGamePath, mode));
        return dbFiles == null ? 0 : dbFiles.length;
    }

    private void selectGame(String mode) {
        mGameMode = mode;
        mPrefs.edit().putString("game_mode", mode).apply();
        checkGamePathStatus();
    }

    /** Обновляет всё, что показывает состояние файлов выбранной игры. */
    private void checkGamePathStatus() {
        if (mTextSelectedGame == null) return;
        int green = ContextCompat.getColor(this, R.color.status_green);
        int red = ContextCompat.getColor(this, R.color.status_red);

        if (mGamePath == null || mGamePath.isEmpty()) {
            mTextPathStatus.setText("Путь не указан");
            setStatusIcon(mTextPathStatus, 0, 0);
            mTextPathStatus.setTextColor(red);
            mTextSelectedGame.setText(gameTitle(mGameMode));
            mTextMainStatus.setText("Путь к играм не указан. Откройте «Выбор игры».");
            tintDot(red);
            return;
        }

        int[] cardIds = {R.id.card_soc, R.id.card_cs, R.id.card_cop};
        int[] statusIds = {R.id.status_soc, R.id.status_cs, R.id.status_cop};
        for (int i = 0; i < MODES.length; i++) {
            int n = countArchives(MODES[i]);
            TextView st = findViewById(statusIds[i]);
            if (n > 0) {
                st.setText("Файлы найдены, архивов: " + n);
                st.setTextColor(green);
                setStatusIcon(st, R.drawable.ic_check, green);
            } else {
                st.setText("Файлов нет: положите игру в " + new File(mGamePath, MODES[i]).getAbsolutePath());
                st.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
                setStatusIcon(st, R.drawable.ic_cross, ContextCompat.getColor(this, R.color.text_muted));
            }
            findViewById(cardIds[i]).setBackgroundResource(
                    MODES[i].equals(mGameMode) ? R.drawable.game_card_selected : R.drawable.game_card);
        }

        File dir = new File(mGamePath, mGameMode);
        int n = countArchives(mGameMode);
        mTextSelectedGame.setText(gameTitle(mGameMode));
        if (n > 0) {
            String msg = "Игра найдена (архивов: " + n + ")";
            setStatusIcon(mTextPathStatus, R.drawable.ic_check, green);
            mTextPathStatus.setText(msg + "\n" + dir.getAbsolutePath());
            mTextPathStatus.setTextColor(green);
            mTextMainStatus.setText("Всё готово. Сложность выбирается в меню игры.");
            tintDot(green);
        } else {
            setStatusIcon(mTextPathStatus, R.drawable.ic_cross, red);
            mTextPathStatus.setText("Нет файлов игры в " + dir.getAbsolutePath()
                    + "\nСкопируйте туда gamedata.db* и папку gamedata.");
            mTextPathStatus.setTextColor(red);
            mTextMainStatus.setText("Файлы игры не найдены. Откройте «Выбор игры».");
            tintDot(red);
        }
    }

    /** Маленькая векторная иконка слева от текста статуса (вместо символов ✓ ✗ ⚠). */
    private void setStatusIcon(TextView tv, int resId, int color) {
        if (tv == null) return;
        if (resId == 0) {
            tv.setCompoundDrawablesRelative(null, null, null, null);
            return;
        }
        android.graphics.drawable.Drawable d = ContextCompat.getDrawable(this, resId);
        if (d == null) return;
        d = d.mutate();
        d.setTint(color);
        int sz = Math.round(14 * getResources().getDisplayMetrics().density);
        d.setBounds(0, 0, sz, sz);
        tv.setCompoundDrawablesRelative(d, null, null, null);
        tv.setCompoundDrawablePadding(Math.round(6 * getResources().getDisplayMetrics().density));
    }

    private void tintDot(int color) {
        if (mDotStatus != null && mDotStatus.getBackground() != null) {
            android.graphics.drawable.Drawable d = mDotStatus.getBackground().mutate();
            d.setTint(color);
        }
    }

    /**
     * «ИГРАТЬ»: запускает уже настроенную конфигурацию. Никаких -start и
     * -difficulty: движок открывает главное меню, а новую игру и сложность
     * игрок выбирает сам.
     */
    private void launchGame() {
        savePreferences();

        final String gameMode = mGameMode;
        String basePath = mGamePath == null ? "" : mGamePath.trim();

        // Каждая игра в своей подпапке: <base>/soc, <base>/cs, <base>/cop.
        // Сохранения, конфиги, логи и gamedata.db разных игр не смешиваются.
        final String pathStr = basePath.isEmpty() ? basePath : new File(basePath, gameMode).getAbsolutePath();
        File dir = new File(pathStr);
        if (!pathStr.isEmpty() && !dir.exists()) {
            try {
                dir.mkdirs();
            } catch (Exception ignored) {}
        }

        // Без архивов игра стартует в серый экран: предупредим заранее.
        File[] archives = pathStr.isEmpty() ? null : AppLog.collectDatabaseArchives(dir);
        if (archives == null || archives.length == 0) {
            new AlertDialog.Builder(this)
                    .setTitle("Файлы игры не найдены")
                    .setMessage("В папке\n" + pathStr + "\nнет архивов gamedata.db*. "
                            + "Без них игра покажет серый экран.")
                    .setPositiveButton("Выбор игры", (d, w) -> showGamesPanel())
                    .setNegativeButton("Всё равно запустить", (d, w) -> startEngine(gameMode, pathStr))
                    .show();
            return;
        }
        startEngine(gameMode, pathStr);
    }

    private void startEngine(String gameMode, String pathStr) {
        StringBuilder argsBuilder = new StringBuilder();

        if ("cs".equals(gameMode)) {
            argsBuilder.append("-cs ");
        } else if ("cop".equals(gameMode)) {
            argsBuilder.append("-cop ");
        } else {
            argsBuilder.append("-soc ");
        }

        // OpenGL (-rgl) is always enabled
        argsBuilder.append("-rgl ");
        if (mCheckNoIntro.isChecked()) {
            argsBuilder.append("-nointro ");
        }
        if (mCheckNoSound.isChecked()) {
            argsBuilder.append("-nosound ");
        }
        if (mCheckNoShadows.isChecked()) {
            // -nodlights drops every dynamic light source outright (Light_DB.cpp),
            // which is cheaper on a mobile GPU than -noshadows and still reads as
            // "shadows gone" because without lights there is nothing casting them.
            argsBuilder.append("-nodlights ");
        }
        if (!mCheckDLights.isChecked()) {
            // Static lighting: skip the dynamic sun shadow pass only. The sun keeps
            // lighting the scene, so this stays readable -- unlike -nodlights.
            argsBuilder.append("-sunstatic ");
        }

        String customArgs = mEditCustomArgs.getText().toString().trim();
        if (!customArgs.isEmpty()) {
            argsBuilder.append(customArgs).append(" ");
        }

        if (!pathStr.isEmpty()) {
            argsBuilder.append("-game_path \"").append(pathStr).append("\" ");
        }

        ResolutionItem selectedRes = (mSpinnerResolution.getSelectedItem() instanceof ResolutionItem)
            ? (ResolutionItem) mSpinnerResolution.getSelectedItem()
            : null;
        int resW = (selectedRes != null) ? selectedRes.width : 0;
        int resH = (selectedRes != null) ? selectedRes.height : 0;

        boolean noSun = mCheckNoSun.isChecked();
        String graphicsPreset = "balanced";
        if (mSpinnerGraphicsPreset.getSelectedItem() instanceof GraphicsPresetItem) {
            graphicsPreset = ((GraphicsPresetItem) mSpinnerGraphicsPreset.getSelectedItem()).id;
        }

        updateUserLtxSettingsIfChanged(pathStr, gameMode, null, mCheckNoShadows.isChecked(),
                mCheckDLights.isChecked(), noSun, mCheckFastDxt.isChecked(), graphicsPreset, resW, resH);

        float sens = Math.max(5, mSeekLookSens.getProgress()) / 10.0f;
        float opacity = Math.max(20, mSeekTouchOpacity.getProgress()) / 100.0f;
        float scale = Math.max(70, mSeekTouchScale.getProgress()) / 100.0f;

        String renderBackend = "native";
        if (mSpinnerRenderBackend.getSelectedItem() instanceof RenderBackendItem) {
            renderBackend = ((RenderBackendItem) mSpinnerRenderBackend.getSelectedItem()).id;
        }

        String finalArgs = argsBuilder.toString().trim();
        AppLog.i("Launcher", "Starting OpenXRayActivity (main menu): renderBackend=" + renderBackend
                + ", args: " + finalArgs);

        Intent gameIntent = new Intent(this, OpenXRayActivity.class);
        gameIntent.putExtra("extra_args", finalArgs);
        gameIntent.putExtra("extra_game_path", pathStr);
        gameIntent.putExtra("extra_game_mode", gameMode);
        gameIntent.putExtra("extra_render_backend", renderBackend);
        gameIntent.putExtra("extra_look_sensitivity", sens);
        gameIntent.putExtra("extra_touch_opacity", opacity);
        gameIntent.putExtra("extra_touch_scale", scale);

        startActivity(gameIntent);
    }

    /**
     * A session marker that survived the last run means the process died
     * (native SIGSEGV/SIGABRT or an uncaught Java exception), because a clean
     * exit deletes it in OpenXRayActivity.onDestroy. Offer to send the logs to
     * GitHub before the user can touch anything else.
     */
    private void offerCrashReportIfPending() {
        final CrashReporter.CrashInfo info = CrashReporter.checkPendingCrash(this);
        if (info == null) return;

        final String full = CrashReporter.buildReport(info, this);
        StringBuilder msg = new StringBuilder();
        msg.append(CrashReporter.describeCrash(info));
        if (info.crashLine != null) {
            msg.append("\n\n").append(info.crashLine);
        }
        msg.append('\n');

        androidx.appcompat.app.AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("Предыдущий сеанс завершился аварийно")
                .setCancelable(true);

        if (GitHubIssueReporter.isConfigured()) {
            // One tap: the logs go straight into an issue, no browser, no CAPTCHA.
            msg.append("\nОтправить логи в трекер ошибок GitHub?");
            b.setMessage(msg.toString())
                .setPositiveButton("Отправить отчёт", (d, w) -> sendCrashReportToGitHub(info));
        } else {
            msg.append("\nАвтоматическая отправка в этой сборке не включена "
                    + "(при сборке не задан токен репозитория).");
            b.setMessage(msg.toString());
        }
        b.setNeutralButton("Копировать логи", (d, w) -> copyLogs(full));
        b.setNegativeButton("Пропустить", (d, w) -> CrashReporter.consumePendingCrash(this));
        b.show();
    }

    private void copyLogs(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Логи OpenXRay", text));
            Toast.makeText(this, "Логи скопированы", Toast.LENGTH_SHORT).show();
        }
    }

    /** One tap -> one issue. Falls back to a copyable report if it cannot send. */
    private void sendCrashReportToGitHub(final CrashReporter.CrashInfo info) {
        sendCrashReportToGitHub(info, true);
    }

    private void sendCrashReportToGitHub(final CrashReporter.CrashInfo info,
                                         final boolean consumeOnSuccess) {
        Toast.makeText(this, "Отправка отчёта…", Toast.LENGTH_SHORT).show();
        GitHubIssueReporter.report(info, this, result -> {
            if (result.success) {
                Toast.makeText(this, result.duplicate
                        ? "Добавлено в существующую задачу" : "Задача создана", Toast.LENGTH_LONG).show();
                // Only a real crash report may clear the pending marker: a
                // diagnostics upload must not silently discard a crash.
                if (consumeOnSuccess) CrashReporter.consumePendingCrash(this);
                GitHubIssueReporter.openIssue(this, result.issueUrl);
            } else {
                // Network/token trouble must not eat the report: keep the text
                // reachable so the user can paste it somewhere by hand.
                Toast.makeText(this, result.error, Toast.LENGTH_LONG).show();
                offerCrashReportFallback(info);
            }
        });
    }

    private void offerCrashReportFallback(final CrashReporter.CrashInfo info) {
        final String full = CrashReporter.buildReport(info, this);
        new AlertDialog.Builder(this)
                .setTitle("Отчёт не отправлен")
                .setMessage("Логи сохранены. Скопируйте их и вставьте "
                        + "в новую задачу или отправьте ZIP-архивом.")
                .setPositiveButton("Повторить", (d, w) -> sendCrashReportToGitHub(info, false))
                .setNeutralButton("Копировать логи", (d, w) -> copyLogs(full))
                .setNegativeButton("Закрыть", null)
                .show();
    }

    /**
     * Manual bug report: the current session's logs, sent to the same issue
     * tracker with one tap. Always available, whether or not a crash happened.
     */
    private void showBugTrackerDialog() {
        String repo = BuildConfig.GITHUB_REPO;
        StringBuilder msg = new StringBuilder();
        msg.append("Сообщить о проблеме в OpenXRay Android.\n\n");
        msg.append("Трекер: ").append(repo).append('\n');
        if (GitHubIssueReporter.isConfigured()) {
            msg.append("\nОдно нажатие отправит лог движка, logcat и лог приложения как новую "
                    + "задачу. Больше ничего не отправляется.");
        } else {
            msg.append("\nАвтоматическая отправка в этой сборке не включена, поэтому вы получите "
                    + "логи, чтобы вставить их в новую задачу вручную.");
        }

        androidx.appcompat.app.AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("Сообщить об ошибке")
                .setMessage(msg.toString())
                .setNegativeButton("Закрыть", null);

        if (GitHubIssueReporter.isConfigured()) {
            b.setPositiveButton("Отправить отчёт", (d, w) ->
                    sendCrashReportToGitHub(CrashReporter.forCurrentSession(this), false));
        }
        // Falls back to the repository page, so this button is useful whether or
        // not a token was baked in.
        b.setNeutralButton("Открыть трекер", (d, w) -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/" + repo + "/issues")));
            } catch (Exception e) {
                Toast.makeText(this, "Не удалось открыть браузер", Toast.LENGTH_SHORT).show();
            }
        });
        // The crash dialog offers "Копировать логи", but this manual path only offered
        // "Отправить отчёт" or the browser -- so once the crash marker had been
        // consumed there was no way left to get the report text out at all.
        // Always keep it reachable, token or no token.
        b.setNeutralButton("Копировать логи", (d, w) ->
                copyLogs(CrashReporter.buildReport(CrashReporter.forCurrentSession(this), this)));
        b.show();
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                }
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[] {
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, PERMISSION_REQUEST_CODE);
            }
        }
    }

    private void showLogsDialog() {
        String basePath = mGamePath;
        String pathStr = (basePath != null && !basePath.isEmpty())
            ? new File(basePath, selectedGameMode()).getAbsolutePath()
            : basePath;
        File logDir = (pathStr != null && !pathStr.isEmpty())
            ? new File(pathStr)
            : new File(Environment.getExternalStorageDirectory(), "OpenXRay");

        File appLogFile = new File(logDir, "openxray_app.log");
        File logcatFile = new File(logDir, "openxray_logcat.log");
        File appLogBkpFile = new File(logDir, "openxray_app.log.bkp");
        File logcatBkpFile = new File(logDir, "openxray_logcat.log.bkp");
        File engineLog = AppLog.findEngineLog(logDir);

        final String fullLog = buildFullLog(logDir);
        final String preview = fullLog.length() > 100000
                ? "[показан хвост лога, 100000 из " + fullLog.length() + " символов; кнопка копирует ВЕСЬ лог]\n"
                    + fullLog.substring(fullLog.length() - 100000)
                : fullLog;

        TextView tv = new TextView(this);
        tv.setText(preview);
        tv.setTextSize(11);
        tv.setPadding(30, 20, 30, 20);
        tv.setTextIsSelectable(true);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(tv);

        TextView copyBtn = new TextView(this);
        copyBtn.setText("КОПИРОВАТЬ ВЕСЬ ЛОГ");
        copyBtn.setTextColor(0xFFE8A33D);
        copyBtn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        copyBtn.setGravity(android.view.Gravity.CENTER);
        copyBtn.setPadding(20, 28, 20, 28);
        copyBtn.setBackgroundResource(R.drawable.pill_button);
        copyBtn.setOnClickListener(v -> copyFullLog(fullLog, logDir));

        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.widget.LinearLayout.LayoutParams bp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.setMargins(24, 16, 24, 8);
        box.addView(copyBtn, bp);
        box.addView(sv, new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        androidx.appcompat.app.AlertDialog.Builder builder = new AlertDialog.Builder(this)
            .setTitle("Логи OpenXRay")
            .setView(box)
            .setPositiveButton("Отправить ZIP", (dialog, which) -> {
                File report = new File(logDir, "openxray_report.txt");
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(report, false))) {
                    writer.write(fullLog);
                } catch (Exception e) {
                    Toast.makeText(this, "Не удалось сохранить отчёт: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    return;
                }
                shareLogsZip(logDir, report);
            });

        // Diagnostics upload for "broken but not crashing" reports. Consumes no
        // crash marker: a pending crash stays reportable afterwards.
        if (GitHubIssueReporter.isConfigured()) {
            builder.setNeutralButton("Отправить на GitHub", (dialog, which) ->
                    sendCrashReportToGitHub(CrashReporter.forCurrentSession(this), false));
        }
        builder.setNegativeButton("Закрыть", null);
        builder.show();
    }

    /** Собирает ВЕСЬ лог (без обрезки по строкам) в один текст. */
    private String buildFullLog(File logDir) {
        StringBuilder c = new StringBuilder();
        c.append("=== ОБЩИЙ ЛОГ OpenXRay ===\n");
        c.append("Log directory: ").append(logDir.getAbsolutePath()).append("\n\n");
        File engineLog = AppLog.findEngineLog(logDir);
        appendWholeFile(c, "openxray_app.log (текущий сеанс)", new File(logDir, "openxray_app.log"));
        appendWholeFile(c, "openxray_app.log.bkp (прошлый сеанс)", new File(logDir, "openxray_app.log.bkp"));
        if (engineLog != null) {
            appendWholeFile(c, engineLog.getName() + " (лог движка)", engineLog);
            appendWholeFile(c, engineLog.getName().replaceAll("\\.log$", ".bkp") + " (лог движка, прошлый сеанс)",
                    new File(engineLog.getParentFile(), engineLog.getName().replaceAll("\\.log$", ".bkp")));
        } else {
            c.append("=== [Лог движка] ===\nOpenXRay_*.log не найден: движок не дошёл до создания лога.\n\n");
        }
        appendWholeFile(c, "fsgame.ltx", new File(logDir, "fsgame.ltx"));
        appendWholeFile(c, "frame_trace.log", new File(logDir, "frame_trace.log"));
        appendWholeFile(c, "openxray_logcat.log", new File(logDir, "openxray_logcat.log"));
        appendWholeFile(c, "openxray_logcat.log.bkp (прошлый сеанс)", new File(logDir, "openxray_logcat.log.bkp"));
        String crashBuffer = readLogcatBuffer("crash");
        if (crashBuffer != null && !crashBuffer.isEmpty()) {
            c.append("=== [logcat -b crash (нативный краш)] ===\n").append(crashBuffer).append("\n");
        }
        return c.toString();
    }

    private void appendWholeFile(StringBuilder c, String title, File f) {
        if (f == null || !f.exists() || f.length() == 0) return;
        c.append("=== [").append(title).append("] ===\n");
        String t = AppLog.readTextFile(f);
        c.append(t != null ? t : "(не читается)").append("\n");
    }

    /** Сохраняет общий лог в openxray_full.log и кладёт его в буфер обмена целиком. */
    private void copyFullLog(String full, File logDir) {
        try (BufferedWriter w = new BufferedWriter(new FileWriter(new File(logDir, "openxray_full.log"), false))) {
            w.write(full);
        } catch (Exception ignored) {}
        // Буфер обмена Android ограничен ~1 МБ; если лог больше, копируем самый свежий хвост.
        final int LIMIT = 400000;
        String text = full;
        boolean cut = false;
        if (text.length() > LIMIT) {
            text = "[начало обрезано под лимит буфера, полный лог: " + logDir.getAbsolutePath()
                    + "/openxray_full.log]\n" + text.substring(text.length() - LIMIT);
            cut = true;
        }
        try {
            ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cb.setPrimaryClip(ClipData.newPlainText("Логи OpenXRay", text));
            Toast.makeText(this, cut ? "Скопирован хвост лога (полный в openxray_full.log)"
                    : "Весь лог скопирован", Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(this, "Не удалось скопировать: " + t.getMessage()
                    + ". Полный лог: openxray_full.log", Toast.LENGTH_LONG).show();
        }
    }

    private void shareLogsZip(File logDir, File report) {
        File zipFile = new File(logDir, "openxray_logs.zip");
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                new java.io.FileOutputStream(zipFile))) {
            addToZip(zos, report, report.getName());
            for (String name : new String[]{
                    "openxray_app.log", "openxray_app.log.bkp",
                    "openxray_logcat.log", "openxray_logcat.log.bkp",
                    "frame_trace.log", "fsgame.ltx"}) {
                File f = new File(logDir, name);
                if (f.exists() && f.length() > 0) addToZip(zos, f, name);
            }
            File engineLog = AppLog.findEngineLog(logDir);
            if (engineLog != null) {
                addToZip(zos, engineLog, engineLog.getName());
                File bkp = new File(engineLog.getParentFile(),
                        engineLog.getName().replaceAll("\\.log$", ".bkp"));
                if (bkp.exists() && bkp.length() > 0) addToZip(zos, bkp, bkp.getName());
            }
            File userLtx = new File(logDir, "_appdata_/user.ltx");
            if (userLtx.exists()) addToZip(zos, userLtx, "user.ltx");
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось создать архив: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                this, getPackageName() + ".fileprovider", zipFile);
        android.content.Intent share = new android.content.Intent(android.content.Intent.ACTION_SEND);
        share.setType("application/zip");
        share.putExtra(android.content.Intent.EXTRA_STREAM, uri);
        share.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        share.setClipData(android.content.ClipData.newUri(getContentResolver(), "logs", uri));
        startActivity(android.content.Intent.createChooser(share, "Поделиться логами"));
    }

    private static void addToZip(java.util.zip.ZipOutputStream zos, File f, String entryName)
            throws java.io.IOException {
        zos.putNextEntry(new java.util.zip.ZipEntry(entryName));
        try (java.io.FileInputStream fis = new java.io.FileInputStream(f)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = fis.read(buf)) > 0) zos.write(buf, 0, n);
        }
        zos.closeEntry();
    }

    // Reads a logcat buffer without -f. Returns null when the app is not
    // allowed to read it (crash buffer needs READ_LOGS on modern Android).
    private String readLogcatBuffer(String buffer) {
        try {
            ProcessBuilder pb = new ProcessBuilder("logcat", "-b", buffer, "-d", "-v", "threadtime", "-t", "80");
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            p.waitFor();
            return sb.toString().trim();
        } catch (Exception e) {
            return null;
        }
    }

    private String readLastLines(File file, int maxLines) {
        if (!file.exists()) return "";
        java.util.LinkedList<String> lines = new java.util.LinkedList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
                if (lines.size() > maxLines) {
                    lines.removeFirst();
                }
            }
        } catch (Exception e) {
            return "Error reading log: " + e.getMessage();
        }
        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            sb.append(l).append("\n");
        }
        return sb.toString();
    }

    // Engine _preset tokens (see qpreset_token in xrRender_console.cpp).
    // "none" means the launcher does not manage graphics at all (set in game).
    private static String presetTokenForId(String presetId) {
        if ("none".equals(presetId)) return null;
        if ("minimum".equals(presetId)) return "Минимум";
        if ("low".equals(presetId)) return "Низкие";
        if ("high".equals(presetId)) return "Высокие";
        if ("extreme".equals(presetId)) return "Экстрим";
        if ("potato".equals(presetId)) return "Минимум";
        return "Default";
    }

    // Extra cheap settings applied on top of Minimum for the Potato preset.
    // All names verified against xrRender_console.cpp console commands.
    private static final String[][] POTATO_EXTRA_SETTINGS = {
        {"r2_ssao_mode", "disabled"},
        {"r2_ssao", "off"},
        {"r2_sun_shafts", "st_opt_off"},
        {"r2_sun_quality", "st_opt_low"},
        {"r2_smap_size", "1024"},
        {"r2_dof_enable", "off"},
        {"r3_msaa", "st_opt_off"},
        {"r2_volumetric_lights", "off"},
        {"r3_volumetric_smoke", "off"},
        {"r3_dynamic_wet_surfaces", "off"},
        {"r3_water_refl", "st_opt_off"},
        {"r2_steep_parallax", "off"},
        {"r2_detail_bump", "off"},
        {"r2_soft_water", "off"},
        {"r2_soft_particles", "off"},
        {"r__tf_aniso", "1"},
        {"r__detail_density", "0.3"},
        {"r2_aa", "off"},
    };

    // Graphics keys fully managed by the launcher: old values are dropped from
    // user.ltx and re-appended after the _preset line, so switching presets
    // never leaves stale overrides behind.
    private static final String[] MANAGED_GRAPHICS_KEYS = {
        "_preset",
        "r2_sun", "r2_sun_details", "r__actor_shadow",
        "r2_volumetric_lights", "r2_slight_fade",
        "r2_ssao_mode", "r2_ssao", "r2_sun_shafts", "r2_sun_quality",
        "r2_smap_size", "r2_dof_enable", "r3_msaa",
        "r3_volumetric_smoke", "r3_dynamic_wet_surfaces", "r3_water_refl",
        "r2_steep_parallax", "r2_detail_bump", "r2_soft_water",
        "r2_soft_particles", "r__tf_aniso", "r__detail_density", "r2_aa",
        // Extra aspects covered per preset below.
        "r2_tf_mipbias", "r__geometry_lod", "r2_ssa_lod_a", "r2_ssa_lod_b",
        "r2_sun_far", "rs_skeleton_update", "r__fast_dxt",
    };

    // Full-aspect values per graphics preset. Key names verified against
    // xrRender_console.cpp console commands and their min/max ranges.
    // Texture quality: r__tf_aniso (filtering) + r2_tf_mipbias (detail).
    // Draw distance: r2_ssa_lod_a/b (object cull) + r2_sun_far.
    // Model quality: r__geometry_lod + rs_skeleton_update (lower = smoother).
    private static java.util.Map<String, String[][]> presetAspectSettings() {
        java.util.Map<String, String[][]> m = new java.util.LinkedHashMap<>();
        String[][] potato = {
            {"r__tf_aniso", "1"}, {"r2_tf_mipbias", "1.0"},
            {"r__geometry_lod", "0.5"}, {"rs_skeleton_update", "32"},
            {"r2_ssa_lod_a", "32"}, {"r2_ssa_lod_b", "32"},
            {"r__detail_density", "0.3"},
            {"r2_smap_size", "1024"}, {"r2_sun_far", "60"},
        };
        String[][] minimum = {
            {"r__tf_aniso", "2"}, {"r2_tf_mipbias", "0.5"},
            {"r__geometry_lod", "0.6"}, {"rs_skeleton_update", "24"},
            {"r2_ssa_lod_a", "32"}, {"r2_ssa_lod_b", "32"},
            {"r__detail_density", "0.3"},
            {"r2_smap_size", "1024"}, {"r2_sun_far", "70"},
        };
        String[][] low = {
            {"r__tf_aniso", "4"}, {"r2_tf_mipbias", "0"},
            {"r__geometry_lod", "0.75"}, {"rs_skeleton_update", "16"},
            {"r2_ssa_lod_a", "48"}, {"r2_ssa_lod_b", "40"},
            {"r__detail_density", "0.45"},
            {"r2_smap_size", "1536"}, {"r2_sun_far", "80"},
        };
        String[][] balanced = {
            {"r__tf_aniso", "4"}, {"r2_tf_mipbias", "0"},
            {"r__geometry_lod", "0.9"}, {"rs_skeleton_update", "10"},
            {"r2_ssa_lod_a", "64"}, {"r2_ssa_lod_b", "48"},
            {"r__detail_density", "0.6"},
            {"r2_smap_size", "2048"}, {"r2_sun_far", "100"},
        };
        String[][] high = {
            {"r__tf_aniso", "8"}, {"r2_tf_mipbias", "0"},
            {"r__geometry_lod", "1.0"}, {"rs_skeleton_update", "8"},
            {"r2_ssa_lod_a", "64"}, {"r2_ssa_lod_b", "56"},
            {"r__detail_density", "0.75"},
            {"r2_smap_size", "2048"}, {"r2_sun_far", "120"},
        };
        String[][] extreme = {
            {"r__tf_aniso", "16"}, {"r2_tf_mipbias", "0"},
            {"r__geometry_lod", "1.25"}, {"rs_skeleton_update", "4"},
            {"r2_ssa_lod_a", "96"}, {"r2_ssa_lod_b", "64"},
            {"r__detail_density", "0.9"},
            {"r2_smap_size", "3072"}, {"r2_sun_far", "150"},
        };
        m.put("potato", potato);
        m.put("minimum", minimum);
        m.put("low", low);
        m.put("balanced", balanced);
        m.put("high", high);
        m.put("extreme", extreme);
        return m;
    }

    private static boolean isManagedGraphicsKey(String trimmedLine) {
        for (String key : MANAGED_GRAPHICS_KEYS) {
            if (trimmedLine.equals(key)
                    || trimmedLine.startsWith(key + " ")
                    || trimmedLine.startsWith(key + "\t")) {
                return true;
            }
        }
        return false;
    }

    // The launcher rewrites graphics keys in user.ltx ONLY when the
    // launcher-side selection changed since the last launch (or on first
    // run). Otherwise settings tweaked inside the game are left alone —
    // previously every launch wiped them back to the spinner values.
    private void updateUserLtxSettingsIfChanged(String modePathStr, String gameMode, String diff,
            boolean noShadows, boolean dLights, boolean noSun, boolean fastDxt,
            String graphicsPreset, int resW, int resH) {
        // v2: сложность больше не пишется лаунчером, а load_last_save вычищается.
        String sig = "v2|" + graphicsPreset + "|" + resW + "x" + resH + "|"
                + noShadows + "|" + dLights + "|" + noSun + "|" + fastDxt;
        String key = "applied_gfx_signature_" + gameMode;
        String prev = mPrefs.getString(key, null);
        File probe = new File(new File(modePathStr, "_appdata_"), "user.ltx");
        if (sig.equals(prev) && probe.exists()) {
            AppLog.i("Launcher", "Graphics selection unchanged, keeping in-game user.ltx");
            return;
        }
        if ("none".equals(graphicsPreset)) {
            // Preset 'none' means the game owns its graphics settings, so no
            // graphics key is written. The resolution is not a preset key though:
            // it has its own spinner and its own saved preference, and returning
            // early here left that spinner dead -- the choice was persisted in
            // prefs but never reached user.ltx, so the game kept rendering at
            // whatever vid_mode was there last.
            AppLog.i("Launcher", "Graphics preset is 'none', writing resolution only: "
                    + resW + "x" + resH);
        }
        updateUserLtxSettings(modePathStr, diff, noShadows, dLights, noSun, fastDxt, graphicsPreset, resW, resH);
        mPrefs.edit().putString(key, sig).apply();
    }

    private void updateUserLtxSettings(String pathStr, String diff, boolean noShadows, boolean dLights, boolean noSun, boolean fastDxt, String graphicsPreset, int resW, int resH) {
        if (pathStr == null || pathStr.isEmpty()) return;
        try {
            File appDataDir = new File(pathStr, "_appdata_");
            if (!appDataDir.exists()) {
                appDataDir.mkdirs();
            }

            File[] candidates = new File[] {
                new File(appDataDir, "user.ltx"),
                new File(pathStr, "appdata/user.ltx"),
                new File(pathStr, "user.ltx")
            };

            File targetFile = candidates[0];
            for (File f : candidates) {
                if (f.exists()) {
                    targetFile = f;
                    break;
                }
            }

            java.util.Map<String, String> settings = new java.util.LinkedHashMap<>();
            // _preset must come first: it loads rspec_*.ltx, lines below override it.
            // presetTokenForId returns null for "none" -- writing that through would
            // put a literal "_preset null" into user.ltx.
            String presetToken = presetTokenForId(graphicsPreset);
            if (presetToken != null) {
                settings.put("_preset", presetToken);
            }
            // Full-aspect values for this preset (texture quality, draw
            // distance, model quality...). Explicit sun/light checkbox
            // choices below still win over these.
            String[][] aspects = presetAspectSettings().get(graphicsPreset);
            if (aspects != null) {
                for (String[] kv : aspects) {
                    settings.put(kv[0], kv[1]);
                }
            }
            if (diff != null && !diff.isEmpty()) {
                settings.put("g_game_difficulty", diff);
            }
            // Fast CPU DXT decode (bcdec); managed so the checkbox state
            // always matches the engine, like the other graphics keys.
            settings.put("r__fast_dxt", fastDxt ? "1" : "0");
            if (resW > 0 && resH > 0) {
                settings.put("vid_mode", resW + "x" + resH);
            } else {
                DisplayMetrics metrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
                int screenW = Math.max(metrics.widthPixels, metrics.heightPixels);
                int screenH = Math.min(metrics.widthPixels, metrics.heightPixels);
                if (screenW > 0 && screenH > 0) {
                    settings.put("vid_mode", screenW + "x" + screenH);
                }
            }
            // Only the "No sun" checkbox owns r2_sun. Static lighting deliberately keeps
            // the sun: -sunstatic skips its shadow pass, it does not remove the
            // light, so folding dLights in here used to turn static mode into
            // "no sun" as well. "No shadows" goes through -nodlights now.
            boolean sunOff = noSun;
            if (sunOff) {
                settings.put("r2_sun", "off");
            } else {
                settings.put("r2_sun", "on");
            }
            boolean staticLighting = !dLights;
            if (staticLighting) {
                settings.put("r2_sun_details", "off");
                settings.put("r__actor_shadow", "off");
                settings.put("r2_volumetric_lights", "off");
                // r2_slight_fade is registered for 0.2..1 (xrRender_console.cpp)
                // and CCC_Float rejects out-of-range values instead of clamping,
                // so the old 0.05 was silently dropped by the engine. Left unset
                // here on purpose -- with the sun shadow pass gone it has nothing
                // left to modulate.
            } else {
                settings.put("r2_sun_details", "on");
                settings.put("r__actor_shadow", "on");
                settings.put("r2_volumetric_lights", "on");
                settings.put("r2_slight_fade", "0.5");
            }
            if ("potato".equals(graphicsPreset)) {
                for (String[] kv : POTATO_EXTRA_SETTINGS) {
                    // Don't stomp explicit sun/light choices made above.
                    if (!settings.containsKey(kv[0])) {
                        settings.put(kv[0], kv[1]);
                    }
                }
            }

            if ("none".equals(graphicsPreset)) {
                // The preset is "none": the game owns its graphics settings, so
                // nothing above may be written -- doing so would overwrite
                // whatever the user set from the in-game console. The resolution
                // is the one value the launcher owns outright, so keep that and
                // drop the rest.
                String vid = settings.get("vid_mode");
                java.util.Map<String, String> resolutionOnly = new java.util.LinkedHashMap<>();
                if (vid != null) {
                    resolutionOnly.put("vid_mode", vid);
                }
                settings = resolutionOnly;
            }

            java.util.List<String> lines = new java.util.ArrayList<>();
            java.util.Set<String> updatedKeys = new java.util.HashSet<>();

            if (targetFile.exists()) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(targetFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String trimmed = line.trim();
                        if (isManagedGraphicsKey(trimmed)
                                || trimmed.startsWith("load_last_save")) {
                            // load_last_save пропускал меню и грузил последнее сохранение.
                            continue;
                        }
                        boolean replaced = false;
                        for (java.util.Map.Entry<String, String> entry : settings.entrySet()) {
                            if (trimmed.startsWith(entry.getKey() + " ")) {
                                lines.add(entry.getKey() + " " + entry.getValue());
                                updatedKeys.add(entry.getKey());
                                replaced = true;
                                break;
                            }
                        }
                        if (!replaced) {
                            lines.add(line);
                        }
                    }
                }
            } else {
                lines.add("renderer renderer_rgl");
            }

            for (java.util.Map.Entry<String, String> entry : settings.entrySet()) {
                if (!updatedKeys.contains(entry.getKey())) {
                    lines.add(entry.getKey() + " " + entry.getValue());
                }
            }

            boolean hasQuickSave = false;
            boolean hasQuickLoad = false;
            boolean hasQuit = false;
            boolean hasMedkit = false;
            boolean hasBandage = false;
            boolean hasLookoutL = false;
            boolean hasLookoutR = false;
            boolean hasWpn1 = false, hasWpn2 = false, hasWpn3 = false, hasWpn4 = false, hasWpn5 = false, hasWpn6 = false;

            for (String l : lines) {
                String tl = l.trim();
                if (tl.startsWith("bind quick_save ") || tl.startsWith("bind_sec quick_save ")) hasQuickSave = true;
                if (tl.startsWith("bind quick_load ") || tl.startsWith("bind_sec quick_load ")) hasQuickLoad = true;
                if (tl.startsWith("bind quit ") || tl.startsWith("bind_sec quit ")) hasQuit = true;
                if (tl.startsWith("bind use_medkit ") || tl.startsWith("bind_sec use_medkit ")) hasMedkit = true;
                if (tl.startsWith("bind use_bandage ") || tl.startsWith("bind_sec use_bandage ")) hasBandage = true;
                if (tl.startsWith("bind llookout ") || tl.startsWith("bind_sec llookout ")) hasLookoutL = true;
                if (tl.startsWith("bind rlookout ") || tl.startsWith("bind_sec rlookout ")) hasLookoutR = true;
                if (tl.startsWith("bind wpn_1 ") || tl.startsWith("bind_sec wpn_1 ")) hasWpn1 = true;
                if (tl.startsWith("bind wpn_2 ") || tl.startsWith("bind_sec wpn_2 ")) hasWpn2 = true;
                if (tl.startsWith("bind wpn_3 ") || tl.startsWith("bind_sec wpn_3 ")) hasWpn3 = true;
                if (tl.startsWith("bind wpn_4 ") || tl.startsWith("bind_sec wpn_4 ")) hasWpn4 = true;
                if (tl.startsWith("bind wpn_5 ") || tl.startsWith("bind_sec wpn_5 ")) hasWpn5 = true;
                if (tl.startsWith("bind wpn_6 ") || tl.startsWith("bind_sec wpn_6 ")) hasWpn6 = true;
            }
            if (!hasQuickSave) {
                lines.add("bind quick_save kF6");
                lines.add("bind_sec quick_save kF5");
            }
            if (!hasQuickLoad) {
                lines.add("bind quick_load kF7");
                lines.add("bind_sec quick_load kF9");
            }
            if (!hasQuit) {
                lines.add("bind quit kESCAPE");
            }
            if (!hasMedkit) {
                lines.add("bind use_medkit kLBRACKET");
            }
            if (!hasBandage) {
                lines.add("bind use_bandage kRBRACKET");
            }
            if (!hasLookoutL) {
                lines.add("bind llookout kQ");
            }
            if (!hasLookoutR) {
                lines.add("bind rlookout kE");
            }
            if (!hasWpn1) lines.add("bind wpn_1 k1");
            if (!hasWpn2) lines.add("bind wpn_2 k2");
            if (!hasWpn3) lines.add("bind wpn_3 k3");
            if (!hasWpn4) lines.add("bind wpn_4 k4");
            if (!hasWpn5) lines.add("bind wpn_5 k5");
            if (!hasWpn6) lines.add("bind wpn_6 k6");

            try (java.io.BufferedWriter writer = new java.io.BufferedWriter(new java.io.FileWriter(targetFile))) {
                for (String l : lines) {
                    writer.write(l);
                    writer.newLine();
                }
            }
        } catch (Exception ignored) {}
    }

    private void openFolderPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(intent, BROWSE_FOLDER_REQUEST_CODE);
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть выбор папки", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BROWSE_FOLDER_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            Uri treeUri = data.getData();
            if (treeUri != null) {
                String path = getPathFromTreeUri(treeUri);
                if (path != null && !path.isEmpty()) {
                    mEditGamePath.setText(path);
                    checkGamePathStatus();
                }
            }
        }
    }

    private String getPathFromTreeUri(Uri treeUri) {
        try {
            String docId = android.provider.DocumentsContract.getTreeDocumentId(treeUri);
            if (docId != null) {
                String[] split = docId.split(":");
                if (split.length > 0) {
                    String type = split[0];
                    String relativePath = split.length > 1 ? split[1] : "";
                    if ("primary".equalsIgnoreCase(type)) {
                        return Environment.getExternalStorageDirectory() + (relativePath.isEmpty() ? "" : "/" + relativePath);
                    } else {
                        return "/storage/" + type + (relativePath.isEmpty() ? "" : "/" + relativePath);
                    }
                }
            }
        } catch (Exception ignored) {}
        return treeUri.getPath();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            checkGamePathStatus();
        }
    }

    public static class ResolutionItem {
        public final String title;
        public final int width;
        public final int height;

        public ResolutionItem(String title, int width, int height) {
            this.title = title;
            this.width = width;
            this.height = height;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private void setupResolutionSpinner() {
        mResolutionList.clear();

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        int screenW = Math.max(metrics.widthPixels, metrics.heightPixels);
        int screenH = Math.min(metrics.widthPixels, metrics.heightPixels);
        if (screenW <= 0) screenW = 1920;
        if (screenH <= 0) screenH = 1080;
        float aspect = (float) screenW / (float) screenH;

        // 1. Native resolution
        mResolutionList.add(new ResolutionItem("Родное: " + screenW + "x" + screenH + " (экран)", 0, 0));

        // 2. Aspect-ratio matched resolutions (preserving full-width screen without stretching)
        if (Math.abs(aspect - (16.0f / 9.0f)) > 0.05f) {
            int w1080 = Math.round(1080 * aspect);
            int w720 = Math.round(720 * aspect);
            int w540 = Math.round(540 * aspect);
            mResolutionList.add(new ResolutionItem("1080p по экрану (" + w1080 + "x1080)", w1080, 1080));
            mResolutionList.add(new ResolutionItem("720p по экрану (" + w720 + "x720)", w720, 720));
            mResolutionList.add(new ResolutionItem("540p по экрану (" + w540 + "x540)", w540, 540));
        }

        // 3. Standard fixed resolutions
        mResolutionList.add(new ResolutionItem("Full HD (1920x1080, 16:9)", 1920, 1080));
        mResolutionList.add(new ResolutionItem("720p (1280x720, 16:9)", 1280, 720));
        mResolutionList.add(new ResolutionItem("qHD (960x540, 16:9)", 960, 540));
        mResolutionList.add(new ResolutionItem("SVGA (800x600, 4:3)", 800, 600));
        mResolutionList.add(new ResolutionItem("VGA (640x480, 4:3)", 640, 480));

        mResolutionAdapter = new ArrayAdapter<>(
            this,
            R.layout.item_resolution_spinner,
            mResolutionList
        );
        mResolutionAdapter.setDropDownViewResource(R.layout.item_resolution_dropdown);
        mSpinnerResolution.setAdapter(mResolutionAdapter);
    }

    public static class GraphicsPresetItem {
        public final String title;
        public final String id;

        public GraphicsPresetItem(String title, String id) {
            this.title = title;
            this.id = id;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private void setupGraphicsPresetSpinner() {
        mGraphicsPresetList.clear();
        mGraphicsPresetList.add(new GraphicsPresetItem("Нет (настроить в игре)", "none"));
        mGraphicsPresetList.add(new GraphicsPresetItem("Картошка (макс. скорость)", "potato"));
        mGraphicsPresetList.add(new GraphicsPresetItem("Минимум", "minimum"));
        mGraphicsPresetList.add(new GraphicsPresetItem("Низкие", "low"));
        mGraphicsPresetList.add(new GraphicsPresetItem("Сбалансированные", "balanced"));
        mGraphicsPresetList.add(new GraphicsPresetItem("Высокие", "high"));
        mGraphicsPresetList.add(new GraphicsPresetItem("Экстрим", "extreme"));

        mGraphicsPresetAdapter = new ArrayAdapter<>(
            this,
            R.layout.item_resolution_spinner,
            mGraphicsPresetList
        );
        mGraphicsPresetAdapter.setDropDownViewResource(R.layout.item_resolution_dropdown);
        if (mSpinnerGraphicsPreset != null) {
            mSpinnerGraphicsPreset.setAdapter(mGraphicsPresetAdapter);
        }
    }

    public static class RenderBackendItem {
        public final String title;
        public final String id;

        public RenderBackendItem(String title, String id) {
            this.title = title;
            this.id = id;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private void setupRenderBackendSpinner() {
        mRenderBackendList.clear();
        mRenderBackendList.add(new RenderBackendItem("Нативный OpenGL ES", "native"));
        mRenderBackendList.add(new RenderBackendItem("OpenGL ES -> Vulkan (ANGLE)", "angle_vulkan"));
        mRenderBackendList.add(new RenderBackendItem("OpenGL ES -> OpenGL ES (ANGLE)", "angle_gles"));

        mRenderBackendAdapter = new ArrayAdapter<>(
            this,
            R.layout.item_resolution_spinner,
            mRenderBackendList
        );
        mRenderBackendAdapter.setDropDownViewResource(R.layout.item_resolution_dropdown);
        mSpinnerRenderBackend.setAdapter(mRenderBackendAdapter);

        mSpinnerRenderBackend.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                updateRenderBackendStatus();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private boolean isAngleAvailable() {
        try {
            String appLibDir = getApplicationInfo().nativeLibraryDir;
            if (appLibDir != null && new File(appLibDir, "libEGL_angle.so").exists()) {
                return true;
            }
        } catch (Exception ignored) {}
        String[] systemDirs = {
            "/apex/com.android.angle/lib64",
            "/system/lib64/egl",
            "/vendor/lib64/egl"
        };
        for (String dir : systemDirs) {
            if (new File(dir, "libEGL_angle.so").exists()) {
                return true;
            }
        }
        return false;
    }

    private void updateRenderBackendStatus() {
        if (mTextRenderBackendStatus == null || mSpinnerRenderBackend == null) return;
        Object selected = mSpinnerRenderBackend.getSelectedItem();
        if (selected instanceof RenderBackendItem) {
            RenderBackendItem item = (RenderBackendItem) selected;
            if ("angle_vulkan".equals(item.id) || "angle_gles".equals(item.id)) {
                mTextRenderBackendStatus.setVisibility(View.VISIBLE);
                if (isAngleAvailable()) {
                    mTextRenderBackendStatus.setText("Библиотеки ANGLE найдены в APK");
                    setStatusIcon(mTextRenderBackendStatus, R.drawable.ic_check, ContextCompat.getColor(this, R.color.status_green));
                    mTextRenderBackendStatus.setTextColor(ContextCompat.getColor(this, R.color.status_green));
                } else {
                    mTextRenderBackendStatus.setText("Библиотеки ANGLE не найдены в APK: будет использован нативный OpenGL ES.");
                    setStatusIcon(mTextRenderBackendStatus, R.drawable.ic_warning, ContextCompat.getColor(this, R.color.amber_primary));
                    mTextRenderBackendStatus.setTextColor(ContextCompat.getColor(this, R.color.amber_primary));
                }
            } else {
                mTextRenderBackendStatus.setVisibility(View.GONE);
            }
        }
    }
}
