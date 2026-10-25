package de.robv.android.xposed;
public final class XSharedPreferences {
    public static Boolean versionCheck;
    public static boolean failRead;
    public XSharedPreferences(String packageName, String file) {
        if (!"cn.plumc.fkoopz".equals(packageName) || !"module_settings".equals(file))
            throw new AssertionError("Incorrect shared settings address");
        if (failRead) throw new SecurityException("Shared prefs unavailable");
    }
    public boolean getBoolean(String key, boolean defaultValue) {
        if (!"check_oopz_version".equals(key)) throw new AssertionError("Incorrect settings key");
        return versionCheck == null ? defaultValue : versionCheck;
    }
}
