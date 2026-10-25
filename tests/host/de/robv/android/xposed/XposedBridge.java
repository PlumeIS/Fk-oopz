package de.robv.android.xposed;
import java.lang.reflect.*;
import java.util.*;
public final class XposedBridge {
    static final Map<Member, XC_MethodHook> hooks = new HashMap<>();
    public static XC_MethodHook.Unhook hookMethod(Member method, XC_MethodHook hook) {
        hooks.put(method, hook); return hook.new Unhook(method);
    }
    public static Object invokeOriginalMethod(Member method, Object target, Object[] args) throws Exception {
        return ((Method) method).invoke(target, args);
    }
    public static Object call(Method method, Object target, Object... args) throws Throwable {
        XC_MethodHook hook = hooks.get(method);
        XC_MethodHook.MethodHookParam p = new XC_MethodHook.MethodHookParam();
        p.thisObject = target; p.args = args;
        if (hook != null) hook.beforeHookedMethod(p);
        if (!p.skip) p.result = method.invoke(target, p.args);
        if (hook != null) hook.afterHookedMethod(p);
        return p.result;
    }
    public static void log(String message) { System.out.println(message); }
}
