package cn.plumc.fkoopz;

/** Per Flutter engine lifecycle. All calls run on the Android main thread. */
final class SplashSession {
    interface Queue { void post(Runnable action); }
    interface Events { void send(String event, String message); }

    private final Queue queue;
    private final Events events;
    private boolean active = true;
    private boolean setupManaged;
    private boolean setupQueued;
    private boolean pending;
    private boolean showing;
    private long generation;

    SplashSession(Queue queue, Events events) {
        this.queue = queue;
        this.events = events;
    }

    void setup() {
        setupManaged = true;
        if (setupQueued || !active) return;
        setupQueued = true;
        queue.post(() -> {
            if (!active) return;
            setupQueued = false;
            events.send("setupSuccess", "初始化成功");
        });
    }

    boolean suppressSetupEvent(String event) {
        return active && setupManaged
                && ("setupSuccess".equals(event) || "setupFailed".equals(event));
    }

    void load(boolean preload) {
        if (!active) return;
        long ticket = ++generation;
        pending = true;
        showing = false;
        queue.post(() -> {
            if (!valid(ticket) || !pending) return;
            events.send("splashAdLoadSuccess", "开屏加载成功");
            if (!preload) show();
        });
    }

    void show() {
        // A cached/standalone show can arrive without a load in this engine.
        // A repeated show after close/cancel is ignored until the next load.
        if (!active || showing) return;
        if (!pending && generation == 0) { pending = true; ++generation; }
        if (!pending) return;
        showing = true;
        long ticket = generation;
        queue.post(() -> {
            if (!valid(ticket) || !showing) return;
            events.send("splashAdOnShow", "开屏已经显示");
            queue.post(() -> {
                if (!valid(ticket) || !showing) return;
                pending = false;
                showing = false;
                events.send("splashAdOnClose", "关闭开屏");
            });
        });
    }

    void cancel() {
        ++generation;
        pending = false;
        showing = false;
    }

    void detach() { cancel(); active = false; }
    boolean isPending() { return active && pending; }
    private boolean valid(long ticket) { return active && generation == ticket; }
}
