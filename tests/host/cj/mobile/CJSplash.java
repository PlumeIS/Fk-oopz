package cj.mobile;
import android.content.Context;
import android.app.Activity;
import android.view.ViewGroup;
import cj.mobile.listener.CJSplashListener;
public final class CJSplash {
    public CJSplashListener listener;
    public int originalLoads, originalShows, destroys;
    public void loadAd(Context c, String id, int w, int h, CJSplashListener l) { ++originalLoads; }
    public void showAd(Activity a, ViewGroup v) { ++originalShows; }
    public void destroy() { ++destroys; }
    public boolean isValid() { return false; }
}
