package cn.plumc.fkoopz;

/** Exact method-channel routing: do not consume reward, banner or unknown commands. */
final class SplashCommands {
    static boolean handle(String method, SplashSession session) {
        switch (method) {
            case "cjLoadAndShowSplashMethod": session.load(false); return true;
            case "cjPreLoadSplashMethod": session.load(true); return true;
            case "cjShowSplashMethod": session.show(); return true;
            default: return false;
        }
    }

    static boolean isSplash(String method) {
        return "cjLoadAndShowSplashMethod".equals(method)
                || "cjPreLoadSplashMethod".equals(method)
                || "cjShowSplashMethod".equals(method);
    }
}
