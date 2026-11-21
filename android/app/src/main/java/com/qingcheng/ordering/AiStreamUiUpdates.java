package com.qingcheng.ordering;

import android.os.Handler;
import java.util.function.Consumer;

/** Coalesces fast deltas to one view update per frame group. */
final class AiStreamUiUpdates {
    private final Handler main;
    private final Consumer<AiStreamState.Update> render;
    private AiStreamState.Update latest;
    private boolean queued, closed;
    private final Runnable flush = this::flush;

    AiStreamUiUpdates(Handler main, Consumer<AiStreamState.Update> render) {
        this.main = main; this.render = render;
    }

    synchronized void offer(AiStreamState.Update update) {
        if (closed) return;
        latest = update;
        if (!queued) { queued = true; main.postDelayed(flush, 40); }
    }

    private void flush() {
        AiStreamState.Update update;
        synchronized (this) {
            if (closed) return;
            update = latest; latest = null; queued = false;
        }
        if (update != null) render.accept(update);
    }

    synchronized void close() { closed = true; latest = null; main.removeCallbacks(flush); }
}
