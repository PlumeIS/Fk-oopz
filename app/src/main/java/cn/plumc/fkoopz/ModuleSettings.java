package cn.plumc.fkoopz;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

final class ModuleSettings {
    static final String PACKAGE = "cn.plumc.fkoopz";
    static final String FILE = "module_settings";
    static final String CHECK_VERSION = "check_oopz_version";

    private ModuleSettings() {}

    /** Read once per Oopz process; missing/unavailable settings keep the existing guard. */
    static boolean isVersionCheckEnabled() {
        try {
            XSharedPreferences preferences = new XSharedPreferences(PACKAGE, FILE);
            return preferences.getBoolean(CHECK_VERSION, true);
        } catch (Throwable error) {
            XposedBridge.log("[FkOopz] Cannot read settings; version check remains enabled: "
                    + error.getClass().getSimpleName());
            return true;
        }
    }
}
