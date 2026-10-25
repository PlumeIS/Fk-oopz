package cn.plumc.fkoopz;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** Small native settings screen; LSPosed shares only this non-sensitive boolean. */
public final class SettingsActivity extends Activity {
    private SharedPreferences preferences;
    private Switch checkVersion;
    private TextView status;
    private boolean updating;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(246, 247, 249));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(24);
        content.setPadding(padding, padding, padding, padding);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            content.setPadding(padding + insets.getSystemWindowInsetLeft(),
                    padding + insets.getSystemWindowInsetTop(),
                    padding + insets.getSystemWindowInsetRight(),
                    padding + insets.getSystemWindowInsetBottom());
            return insets;
        });
        content.addView(label("Oopz 去开屏", 28));
        content.addView(label("模块 1.2.0", 14));
        checkVersion = new Switch(this);
        checkVersion.setText("检查 Oopz 版本");
        checkVersion.setTextSize(18);
        checkVersion.setPadding(0, dp(24), 0, dp(16));
        checkVersion.setChecked(true);
        content.addView(checkVersion);
        content.addView(label("开启：仅在 Oopz 1.1.10 上启用广告拦截，不限制构建号。\n\n"
                + "关闭：允许尝试其他 Oopz 版本；仍会核对广告接口，无法匹配时不会安装相应 Hook。", 16));
        status = label("", 15);
        status.setPadding(0, dp(24), 0, dp(16));
        status.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(status);
        Button appInfo = new Button(this);
        appInfo.setText("打开 Oopz 应用信息");
        appInfo.setOnClickListener(view -> {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:com.weilaishanhai.oopz")));
            } catch (RuntimeException error) {
                status.setText("请在系统设置中找到 Oopz，强制停止后重新打开。");
            }
        });
        content.addView(appInfo);
        content.addView(label("设置保存后，请强制停止 Oopz 再重新打开。\n"
                + "首次使用请先在 LSPosed 启用本模块，并勾选 Oopz 作用域。", 14));
        scroll.addView(content);
        setContentView(scroll);
        checkVersion.setOnCheckedChangeListener((button, enabled) -> {
            if (updating || preferences == null) return;
            boolean previous = preferences.getBoolean(ModuleSettings.CHECK_VERSION, true);
            if (preferences.edit().putBoolean(ModuleSettings.CHECK_VERSION, enabled).commit()) {
                status.setText("已保存：版本检测" + (enabled ? "开启" : "关闭") + "。请重启 Oopz 生效。");
            } else {
                preferences.edit().putBoolean(ModuleSettings.CHECK_VERSION, previous).commit();
                setChecked(previous);
                status.setText("设置保存失败，请检查存储空间后重试。");
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        try {
            // Official LSPosed shared-prefs transport; no private-file fallback that
            // would make the UI say 'saved' while the hooked process cannot read it.
            preferences = getSharedPreferences(ModuleSettings.FILE, Context.MODE_WORLD_READABLE);
            checkVersion.setEnabled(true);
            setChecked(preferences.getBoolean(ModuleSettings.CHECK_VERSION, true));
            status.setText("更改后自动保存，重启 Oopz 生效。");
        } catch (SecurityException error) {
            preferences = null;
            checkVersion.setEnabled(false);
            status.setText("共享设置不可用。请在 LSPosed 启用本模块后重新打开此页面。"
                    + "框架需支持 New XSharedPreferences；不可用时默认保持版本检测开启。");
        }
    }

    private void setChecked(boolean enabled) {
        updating = true;
        checkVersion.setChecked(enabled);
        updating = false;
    }

    private TextView label(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(32, 39, 49));
        view.setPadding(0, dp(8), 0, dp(8));
        return view;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
