package de.robv.android.xposed;
import java.util.*;
public final class XposedHelpers {
    private static final Map<Object, Map<String, Object>> fields = new IdentityHashMap<>();
    public static Class<?> findClass(String name, ClassLoader loader) {
        try { return Class.forName(name, false, loader); }
        catch (Exception e) { throw new IllegalArgumentException(e); }
    }
    public static Object getAdditionalInstanceField(Object target, String key) {
        Map<String, Object> map = fields.get(target); return map == null ? null : map.get(key);
    }
    public static Object setAdditionalInstanceField(Object target, String key, Object value) {
        return fields.computeIfAbsent(target, x -> new HashMap<>()).put(key, value);
    }
    public static Object removeAdditionalInstanceField(Object target, String key) {
        Map<String, Object> map = fields.get(target); return map == null ? null : map.remove(key);
    }
    public static XC_MethodHook.Unhook findAndHookMethod(Class<?> owner, String name, Object... args) {
        try {
            Class<?>[] types = Arrays.copyOf(args, args.length - 1, Class[].class);
            return XposedBridge.hookMethod(owner.getDeclaredMethod(name, types), (XC_MethodHook) args[args.length - 1]);
        } catch (Exception e) { throw new IllegalArgumentException(e); }
    }
    public static XC_MethodHook.Unhook findAndHookMethod(String owner, ClassLoader loader, String name, Object... args) {
        return findAndHookMethod(findClass(owner, loader), name, args);
    }
}
