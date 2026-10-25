package com.example.flutter_cjadsdk_plugin;
import q6.*;
import cj.mobile.CJSplash;
public final class FlutterCjadsdkPlugin {
    public final i channel = new i();
    public CJSplash splashAd;
    public int originalDispatches;
    public void onMethodCall(h call, i.d reply) { ++originalDispatches; }
    public void loadSplashAd(boolean preload, String id) { throw new AssertionError("Original splash load reached"); }
    public void showSplashAd() { throw new AssertionError("Original splash show reached"); }
    public void destorySplash() {}
    public void onDetachedFromEngine(m6.a.b binding) {}
}
