package cn.plumc.fkoopz;

import android.app.Application;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class OopzHook implements IXposedHookLoadPackage {
    private static final String TARGET = "com.weilaishanhai.oopz";
    private static final String PLUGIN = "com.example.flutter_cjadsdk_plugin.FlutterCjadsdkPlugin";
    private static final String STATE = "cn.plumc.fkoopz.session";
    private boolean installed;
    private Handler main;
    private Field channelField;
    private Field splashField;
    private Method emit;

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) {
        if (!TARGET.equals(param.packageName) || !TARGET.equals(param.processName)) return;
        log("Module 1.2.0 [cn.plumc.fkoopz] loaded in " + param.processName);
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class,
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam hook) {
                        installFor((Context) hook.args[0]);
                    }
                });
        // Retry before app code if an optimized attach path did not reach our hook.
        try {
            XposedHelpers.findAndHookMethod(TARGET + ".MyApplication", param.classLoader,
                    "onCreate", new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam hook) {
                            installFor((Context) hook.thisObject);
                        }
                    });
        } catch (Throwable error) { report("Application retry hook unavailable", error); }
    }

    private synchronized void installFor(Context context) {
        if (installed) return;
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(TARGET, 0);
            boolean checkVersion = ModuleSettings.isVersionCheckEnabled();
            log("Version check: " + (checkVersion ? "enabled" : "disabled"));
            if (checkVersion && !"1.1.10".equals(info.versionName)) {
                log("Unsupported target " + info.versionName + "/" + info.versionCode);
                return;
            }
            log("Target " + info.versionName + "/" + info.versionCode + "; checking runtime signatures");
            install(context.getClassLoader());
            installed = true;
            log("Active for " + info.versionName + "/" + info.versionCode);
            // Optional lower-level guard must not roll back the working bridge hooks.
            try { installSdkGuard(context.getClassLoader()); }
            catch (Throwable error) { report("SDK guard unavailable; bridge remains active", error); }
        } catch (Throwable error) { report("Install failed; will retry at Application.onCreate", error); }
    }

    private void install(ClassLoader loader) throws Throwable {
        Class<?> plugin = XposedHelpers.findClass(PLUGIN, loader);
        BridgeBinding binding = new BridgeBinding(plugin);
        Field methodName = binding.command;
        channelField = binding.channel;
        splashField = binding.splash;
        Method reply = binding.reply;
        emit = binding.emit;
        Method load = binding.load;
        Method show = binding.show;
        Method destroy = binding.destroy;
        Method dispatch = binding.dispatch;
        Method detach = binding.detach;
        main = new Handler(Looper.getMainLooper());
        List<XC_MethodHook.Unhook> hooks = new ArrayList<>();
        try {
            hooks.add(XposedBridge.hookMethod(emit, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    SplashSession state = stateOf(p.thisObject);
                    if (state != null && state.suppressSetupEvent((String) p.args[0])) {
                        p.setResult(null);
                    }
                }
            }));
            hooks.add(XposedBridge.hookMethod(dispatch, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                    String command = (String) methodName.get(p.args[0]);
                    if (SplashCommands.isSplash(command)) {
                        SplashSession state = session(p.thisObject);
                        // Consume before Kotlin dispatches its coroutine. Inner-method hooks
                        // alone can miss calls already inlined into optimized callers.
                        p.setResult(null);
                        try { reply.invoke(p.args[1], new Object[]{null}); }
                        catch (Throwable error) { report("MethodChannel reply failed", error); }
                        if ("cjShowSplashMethod".equals(command)) showPlugin(p.thisObject);
                        else SplashCommands.handle(command, state);
                        log("Bridge blocked: " + command);
                    } else if ("cjSdkSetup".equals(command)) {
                        // Keep real SDK initialization for other ad formats, but decouple startup
                        // from its network result. The original method still runs unchanged.
                        session(p.thisObject).setup();
                        log("Startup released from SDK initialization");
                    }
                }
            }));
            hooks.add(XposedBridge.hookMethod(load, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                    session(p.thisObject).load((Boolean) p.args[0]);
                    p.setResult(null);
                    log("Splash bypassed; preload=" + p.args[0]);
                }
            }));
            hooks.add(XposedBridge.hookMethod(show, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                    showPlugin(p.thisObject);
                    p.setResult(null);
                }
            }));
            hooks.add(XposedBridge.hookMethod(destroy, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    SplashSession state = stateOf(p.thisObject);
                    if (state != null) state.cancel();
                    // Keep cleanup for any SDK object created through an optimized caller.
                }
            }));
            hooks.add(XposedBridge.hookMethod(detach, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                    SplashSession state = stateOf(p.thisObject);
                    if (state != null) state.detach();
                    Object ch = channelField.get(p.thisObject);
                    if (ch != null) XposedHelpers.removeAdditionalInstanceField(ch, STATE);
                    XposedHelpers.removeAdditionalInstanceField(p.thisObject, STATE);
                }
            }));
        } catch (Throwable error) {
            for (XC_MethodHook.Unhook hook : hooks) hook.unhook();
            throw error;
        }
        log("Bridge hooks installed; channel=" + channelField.getType().getName()
                + ", emit=" + emit.getName() + ", command=" + methodName.getName());
    }

    private void showPlugin(Object plugin) throws IllegalAccessException {
        SplashSession primary = session(plugin);
        if (primary.isPending()) { primary.show(); return; }
        Object splash = splashField.get(plugin);
        SplashSession sdkState = splash == null ? null : stateOf(splash);
        if (sdkState != null) sdkState.show();
        else primary.show();
    }

    private void installSdkGuard(ClassLoader loader) throws Throwable {
        Class<?> sdk = XposedHelpers.findClass("cj.mobile.CJSplash", loader);
        Class<?> listener = XposedHelpers.findClass("cj.mobile.listener.CJSplashListener", loader);
        Field listenerField = sdk.getDeclaredField("listener");
        listenerField.setAccessible(true);
        Method onLoad = listener.getDeclaredMethod("onLoad");
        Method onShow = listener.getDeclaredMethod("onShow");
        Method onClose = listener.getDeclaredMethod("onClose");
        Method load = sdk.getDeclaredMethod("loadAd", Context.class, String.class,
                int.class, int.class, listener);
        Method show = sdk.getDeclaredMethod("showAd", Activity.class, ViewGroup.class);
        Method destroy = sdk.getDeclaredMethod("destroy");
        Method valid = sdk.getDeclaredMethod("isValid");
        List<XC_MethodHook.Unhook> hooks = new ArrayList<>();
        try {
            hooks.add(XposedBridge.hookMethod(load, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                    Object callback = p.args[4];
                    if (callback == null) return;
                    SplashSession old = stateOf(p.thisObject);
                    if (old != null) old.detach();
                    listenerField.set(p.thisObject, callback);
                    SplashSession state = sdkSession(callback, onLoad, onShow, onClose);
                    XposedHelpers.setAdditionalInstanceField(p.thisObject, STATE, state);
                    p.setResult(null);
                    // The listener decides when to show (preload vs immediate show).
                    state.load(true);
                    log("SDK blocked: CJSplash.loadAd");
                }
            }));
            hooks.add(XposedBridge.hookMethod(show, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                    SplashSession state = stateOf(p.thisObject);
                    if (state == null) {
                        Object callback = listenerField.get(p.thisObject);
                        if (callback == null) return;
                        state = sdkSession(callback, onLoad, onShow, onClose);
                        XposedHelpers.setAdditionalInstanceField(p.thisObject, STATE, state);
                    }
                    p.setResult(null);
                    state.show();
                    log("SDK blocked: CJSplash.showAd");
                }
            }));
            hooks.add(XposedBridge.hookMethod(valid, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    SplashSession state = stateOf(p.thisObject);
                    if (state != null) p.setResult(state.isPending());
                }
            }));
            hooks.add(XposedBridge.hookMethod(destroy, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    SplashSession state = stateOf(p.thisObject);
                    if (state != null) state.cancel();
                    // Keep original cleanup, including any previously cached SDK resources.
                }
            }));
        } catch (Throwable error) {
            for (XC_MethodHook.Unhook hook : hooks) hook.unhook();
            throw error;
        }
        log("CJSplash load/show guard installed");
    }

    private SplashSession sdkSession(Object callback, Method load, Method show, Method close) {
        return new SplashSession(action -> main.post(action), (event, message) -> {
            Method method = "splashAdLoadSuccess".equals(event) ? load
                    : "splashAdOnShow".equals(event) ? show : close;
            try { method.invoke(callback); }
            catch (Throwable error) { report("SDK callback failed: " + event, error); }
        });
    }

    private SplashSession session(Object plugin) throws IllegalAccessException {
        SplashSession state = stateOf(plugin);
        if (state != null) return state;
        Object channel = channelField.get(plugin);
        if (channel == null) throw new IllegalStateException("Flutter channel not attached");
        state = new SplashSession(action -> main.post(action), (event, message) -> {
            try {
                // Bypass only our channel filter; deliver through Flutter's original codec.
                XposedBridge.invokeOriginalMethod(emit, channel, new Object[]{event, message});
                log("Callback: " + event);
            } catch (Throwable error) { report("Callback failed: " + event, error); }
        });
        XposedHelpers.setAdditionalInstanceField(plugin, STATE, state);
        XposedHelpers.setAdditionalInstanceField(channel, STATE, state);
        return state;
    }

    private static SplashSession stateOf(Object object) {
        return (SplashSession) XposedHelpers.getAdditionalInstanceField(object, STATE);
    }

    private static void log(String message) { XposedBridge.log("[FkOopz] " + message); }
    private static void report(String message, Throwable error) {
        log(message + ": " + error.getClass().getSimpleName() + ": " + error.getMessage());
    }
}
