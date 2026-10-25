package cn.plumc.fkoopz;

import android.os.Handler;
import android.content.Context;
import android.app.Activity;
import android.view.ViewGroup;
import java.lang.reflect.Method;
import java.util.*;
import com.example.flutter_cjadsdk_plugin.FlutterCjadsdkPlugin;
import cj.mobile.CJSplash;
import cj.mobile.listener.CJSplashListener;
import de.robv.android.xposed.XposedBridge;

/** Reflective integration tests using a tiny hook harness, not an ART emulator. */
public final class HookWiringTest {
    private static int checks;
    private static void check(boolean b) { if (!b) throw new AssertionError(); ++checks; }
    public static void main(String[] args) throws Throwable {
        OopzHook hook = new OopzHook();
        for (String name : new String[]{"install", "installSdkGuard"}) {
            Method method = OopzHook.class.getDeclaredMethod(name, ClassLoader.class);
            method.setAccessible(true); method.invoke(hook, HookWiringTest.class.getClassLoader());
        }
        Method dispatch = FlutterCjadsdkPlugin.class.getMethod("onMethodCall", q6.h.class, q6.i.d.class);
        FlutterCjadsdkPlugin plugin = new FlutterCjadsdkPlugin();
        int[] replies = {0};
        q6.i.d reply = result -> ++replies[0];
        XposedBridge.call(dispatch, plugin, new q6.h("cjLoadAndShowSplashMethod"), reply);
        check(plugin.originalDispatches == 0); check(replies[0] == 1);
        Handler.drain();
        check(plugin.channel.events.equals(Arrays.asList("splashAdLoadSuccess", "splashAdOnShow", "splashAdOnClose")));
        XposedBridge.call(dispatch, plugin, new q6.h("cjLoadAndShowRewardVideoMethod"), reply);
        check(plugin.originalDispatches == 1); check(replies[0] == 1);
        XposedBridge.call(dispatch, plugin, new q6.h("cjSdkSetup"), reply);
        check(plugin.originalDispatches == 2); Handler.drain();
        Method emit = q6.i.class.getMethod("c", String.class, Object.class);
        int eventCount = plugin.channel.events.size();
        XposedBridge.call(emit, plugin.channel, "setupFailed", "network failed");
        check(plugin.channel.events.size() == eventCount);
        q6.i unrelated = new q6.i();
        XposedBridge.call(emit, unrelated, "setupFailed", "network failed");
        check(unrelated.events.equals(Arrays.asList("setupFailed")));

        CJSplash sdk = new CJSplash();
        List<String> events = new ArrayList<>();
        CJSplashListener listener = new CJSplashListener() {
            public void onLoad() { events.add("load"); }
            public void onShow() { events.add("show"); }
            public void onClose() { events.add("close"); }
        };
        Method load = CJSplash.class.getMethod("loadAd", Context.class, String.class, int.class, int.class, CJSplashListener.class);
        Method show = CJSplash.class.getMethod("showAd", Activity.class, ViewGroup.class);
        Method valid = CJSplash.class.getMethod("isValid");
        Method destroy = CJSplash.class.getMethod("destroy");
        XposedBridge.call(load, sdk, null, "placement", 100, 200, listener);
        Handler.drain(); check(sdk.originalLoads == 0); check(events.equals(Arrays.asList("load")));
        check(Boolean.TRUE.equals(XposedBridge.call(valid, sdk)));
        // Exercise the plugin-to-SDK fallback when only the lower load hook was hit.
        FlutterCjadsdkPlugin fallback = new FlutterCjadsdkPlugin(); fallback.splashAd = sdk;
        XposedBridge.call(FlutterCjadsdkPlugin.class.getMethod("showSplashAd"), fallback);
        Handler.drain(); check(events.equals(Arrays.asList("load", "show", "close")));
        check(sdk.originalShows == 0); check(Boolean.FALSE.equals(XposedBridge.call(valid, sdk)));
        XposedBridge.call(destroy, sdk); check(sdk.destroys == 1);

        CJSplash cached = new CJSplash(); cached.listener = listener; events.clear();
        XposedBridge.call(show, cached, null, null); Handler.drain();
        check(cached.originalShows == 0); check(events.equals(Arrays.asList("show", "close")));
        events.clear();
        XposedBridge.call(load, sdk, null, "placement", 100, 200, listener);
        XposedBridge.call(destroy, sdk); Handler.drain(); check(events.isEmpty());

        FlutterCjadsdkPlugin detached = new FlutterCjadsdkPlugin();
        XposedBridge.call(dispatch, detached, new q6.h("cjLoadAndShowSplashMethod"), reply);
        XposedBridge.call(FlutterCjadsdkPlugin.class.getMethod("onDetachedFromEngine", m6.a.b.class), detached, new m6.a.b());
        Handler.drain(); check(detached.channel.events.isEmpty());
        System.out.println("PASS: " + checks + " reflective hook integration checks");
    }
}
