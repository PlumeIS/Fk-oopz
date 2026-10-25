package de.robv.android.xposed;
public abstract class XC_MethodHook {
    protected void beforeHookedMethod(MethodHookParam p) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam p) throws Throwable {}
    public static final class MethodHookParam {
        public Object thisObject;
        public Object[] args;
        Object result;
        boolean skip;
        public void setResult(Object result) { this.result = result; skip = true; }
    }
    public final class Unhook {
        private final java.lang.reflect.Member method;
        public Unhook(java.lang.reflect.Member method) { this.method = method; }
        public void unhook() { XposedBridge.hooks.remove(method); }
    }
}
