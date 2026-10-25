package android.content;
import android.content.pm.PackageManager;
public class Context {
    private final PackageManager manager;
    public Context(String version, int code) { manager = new PackageManager(version, code); }
    public PackageManager getPackageManager() { return manager; }
    public ClassLoader getClassLoader() { return getClass().getClassLoader(); }
}
