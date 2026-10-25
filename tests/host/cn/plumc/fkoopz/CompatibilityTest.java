package cn.plumc.fkoopz;

import android.content.Context;
import android.os.Handler;
import java.lang.reflect.*;
import java.util.*;
import com.example.flutter_cjadsdk_plugin.FlutterCjadsdkPlugin;
import de.robv.android.xposed.XposedBridge;

public final class CompatibilityTest {
    // Different minified names, identical Flutter contract.
    public static class Call { public String renamedMethod; public Object args; }
    public interface Reply { void renamedSuccess(Object value); }
    public static class Channel { public void renamedInvoke(String event, Object data) {} }
    public static class Plugin {
        public Channel channel;
        public Object splashAd;
        public void onMethodCall(Call c, Reply r) {}
        public void onDetachedFromEngine(Object binding) {}
        public void loadSplashAd(boolean preload, String id) {}
        public void showSplashAd() {}
        public void destorySplash() {}
    }
    public static class AmbiguousChannel {
        public void one(String event, Object data) {}
        public void two(String event, Object data) {}
    }
    public static class AmbiguousPlugin {
        public AmbiguousChannel channel;
        public Object splashAd;
        public void onMethodCall(Call c, Reply r) {}
        public void onDetachedFromEngine(Object binding) {}
    }
    private static int checks;
    private static void check(boolean value) { if (!value) throw new AssertionError(); ++checks; }
    public static void main(String[] args) throws Throwable {
        Method install = OopzHook.class.getDeclaredMethod("installFor", Context.class);
        install.setAccessible(true);
        Field installed = OopzHook.class.getDeclaredField("installed"); installed.setAccessible(true);
        // Regression: the user's 90015 previously returned before installing anything.
        for (int code : new int[]{90013, 90015}) {
            OopzHook hook = new OopzHook();
            install.invoke(hook, new Context("1.1.10", code));
            check(installed.getBoolean(hook));
            FlutterCjadsdkPlugin plugin = new FlutterCjadsdkPlugin();
            Method dispatch = FlutterCjadsdkPlugin.class.getMethod("onMethodCall", q6.h.class, q6.i.d.class);
            XposedBridge.call(dispatch, plugin, new q6.h("cjLoadAndShowSplashMethod"), (q6.i.d) r -> {});
            Handler.drain(); check(plugin.originalDispatches == 0);
            check(plugin.channel.events.equals(Arrays.asList("splashAdLoadSuccess", "splashAdOnShow", "splashAdOnClose")));
        }
        for (String version : new String[]{"1.1.11", null}) {
            OopzHook hook = new OopzHook(); install.invoke(hook, new Context(version, 90015));
            check(!installed.getBoolean(hook));
        }
        BridgeBinding binding = new BridgeBinding(Plugin.class);
        check(binding.command.getName().equals("renamedMethod"));
        check(binding.reply.getName().equals("renamedSuccess"));
        check(binding.emit.getName().equals("renamedInvoke"));
        try { new BridgeBinding(AmbiguousPlugin.class); throw new AssertionError("Ambiguous channel accepted"); }
        catch (NoSuchMethodException expected) { check(true); }
        System.out.println("PASS: " + checks + " compatibility regression checks (90013, 90015, renamed Flutter types)");
    }
}
