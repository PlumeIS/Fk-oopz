package android.content.pm;
public class PackageManager {
    private final PackageInfo info = new PackageInfo();
    public PackageManager(String version, int code) { info.versionName = version; info.versionCode = code; }
    public PackageInfo getPackageInfo(String name, int flags) { return info; }
}
