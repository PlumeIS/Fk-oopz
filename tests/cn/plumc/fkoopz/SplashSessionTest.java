package cn.plumc.fkoopz;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Host JVM tests: real event ordering, cancellation and per-engine isolation. */
public final class SplashSessionTest {
    private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
    private final List<String> events = new ArrayList<>();
    private final SplashSession session = new SplashSession(tasks::add,
            (event, message) -> events.add(event));
    private static int checks;

    private void drain() {
        int remaining = 100;
        while (!tasks.isEmpty()) {
            if (--remaining == 0) throw new AssertionError("Unbounded callback queue");
            tasks.remove().run();
        }
    }
    private void expect(String... expected) {
        if (!events.equals(Arrays.asList(expected))) throw new AssertionError(events.toString());
        checks++;
    }
    private static void check(boolean value) {
        if (!value) throw new AssertionError();
        checks++;
    }
    public static void main(String[] args) {
        SplashSessionTest normal = new SplashSessionTest();
        normal.session.load(false);
        normal.expect(); // Never call Dart synchronously from the load hook.
        normal.drain();
        normal.expect("splashAdLoadSuccess", "splashAdOnShow", "splashAdOnClose");
        normal.session.show(); normal.drain();
        normal.expect("splashAdLoadSuccess", "splashAdOnShow", "splashAdOnClose");

        SplashSessionTest preload = new SplashSessionTest();
        preload.session.load(true); preload.drain();
        preload.expect("splashAdLoadSuccess");
        preload.session.show(); preload.session.show(); preload.drain();
        preload.expect("splashAdLoadSuccess", "splashAdOnShow", "splashAdOnClose");
        preload.session.load(false); preload.drain();
        preload.expect("splashAdLoadSuccess", "splashAdOnShow", "splashAdOnClose",
                "splashAdLoadSuccess", "splashAdOnShow", "splashAdOnClose");

        SplashSessionTest cancel = new SplashSessionTest();
        cancel.session.load(false); cancel.session.cancel(); cancel.drain(); cancel.expect();
        cancel.session.load(false); cancel.tasks.remove().run(); // load delivered
        cancel.tasks.remove().run(); // show delivered, close pending
        cancel.session.cancel(); cancel.drain();
        cancel.expect("splashAdLoadSuccess", "splashAdOnShow");

        SplashSessionTest replaced = new SplashSessionTest();
        replaced.session.load(false); replaced.session.load(true); replaced.drain();
        replaced.expect("splashAdLoadSuccess");

        SplashSessionTest setup = new SplashSessionTest();
        check(!setup.session.suppressSetupEvent("setupFailed"));
        setup.session.setup(); setup.session.setup(); setup.drain();
        setup.expect("setupSuccess");
        check(setup.session.suppressSetupEvent("setupFailed"));
        check(setup.session.suppressSetupEvent("setupSuccess"));
        check(!setup.session.suppressSetupEvent("rewardVideoAdBeRewarded"));
        setup.session.load(false); setup.drain();
        setup.expect("setupSuccess", "splashAdLoadSuccess", "splashAdOnShow", "splashAdOnClose");

        SplashSessionTest detach = new SplashSessionTest();
        detach.session.setup(); detach.session.load(false); detach.session.detach();
        detach.drain(); detach.session.load(false); detach.session.show(); detach.drain();
        detach.expect();
        check(!detach.session.suppressSetupEvent("setupFailed"));
        // Another engine's cancellation never invalidates this one's callbacks.
        replaced.session.show(); replaced.drain();
        replaced.expect("splashAdLoadSuccess", "splashAdOnShow", "splashAdOnClose");

        // Cold-start bridge consumes every splash command before the coroutine runs.
        SplashSessionTest bridge = new SplashSessionTest();
        check(SplashCommands.handle("cjLoadAndShowSplashMethod", bridge.session));
        bridge.drain(); bridge.expect("splashAdLoadSuccess", "splashAdOnShow", "splashAdOnClose");
        check(SplashCommands.handle("cjPreLoadSplashMethod", bridge.session));
        bridge.drain();
        check(SplashCommands.handle("cjShowSplashMethod", bridge.session));
        bridge.drain();
        bridge.expect("splashAdLoadSuccess", "splashAdOnShow", "splashAdOnClose",
                "splashAdLoadSuccess", "splashAdOnShow", "splashAdOnClose");
        check(!SplashCommands.handle("cjLoadAndShowRewardVideoMethod", bridge.session));
        check(!SplashCommands.handle("cjLoadAndShowFullscreenVideoAdMethod", bridge.session));
        check(!SplashCommands.handle("cjSdkSetup", bridge.session));
        check(!SplashCommands.handle("cjShowSplashMethodExtra", bridge.session));
        check(!SplashCommands.isSplash(""));
        check(!SplashCommands.isSplash(null));

        // SDK cached show / fresh engine without a preceding load must still close.
        SplashSessionTest cached = new SplashSessionTest();
        cached.session.show(); cached.drain(); cached.expect("splashAdOnShow", "splashAdOnClose");
        cached.session.show(); cached.drain(); cached.expect("splashAdOnShow", "splashAdOnClose");
        check(!cached.session.isPending());
        cached.session.load(true); check(cached.session.isPending());
        cached.session.cancel(); check(!cached.session.isPending());
        cached.drain(); cached.expect("splashAdOnShow", "splashAdOnClose");

        // SDK listener may synchronously request show from its onLoad callback.
        ArrayDeque<Runnable> sdkTasks = new ArrayDeque<>();
        List<String> sdkEvents = new ArrayList<>();
        SplashSession[] sdk = new SplashSession[1];
        sdk[0] = new SplashSession(sdkTasks::add, (event, message) -> {
            sdkEvents.add(event);
            if ("splashAdLoadSuccess".equals(event)) sdk[0].show();
            if ("splashAdOnClose".equals(event)) sdk[0].cancel();
        });
        sdk[0].load(true);
        while (!sdkTasks.isEmpty()) sdkTasks.remove().run();
        check(sdkEvents.equals(Arrays.asList("splashAdLoadSuccess", "splashAdOnShow", "splashAdOnClose")));
        check(!sdk[0].isPending());
        System.out.println("PASS: " + checks + " lifecycle checks");
    }
}
