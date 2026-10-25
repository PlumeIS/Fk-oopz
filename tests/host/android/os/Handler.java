package android.os;
import java.util.ArrayDeque;
public final class Handler {
    private static final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
    public Handler(Looper ignored) {}
    public boolean post(Runnable task) { tasks.add(task); return true; }
    public static void drain() {
        int budget = 100;
        while (!tasks.isEmpty()) {
            if (--budget == 0) throw new AssertionError("Callback loop");
            tasks.remove().run();
        }
    }
}
