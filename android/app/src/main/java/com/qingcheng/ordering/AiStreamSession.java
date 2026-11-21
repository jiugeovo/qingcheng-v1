package com.qingcheng.ordering;

import java.net.HttpURLConnection;
import java.io.IOException;

/** Per-send transport cancellation; never shares a connection with a newer screen or account. */
final class AiStreamSession {
    private volatile boolean cancelled;
    private volatile HttpURLConnection connection;

    synchronized void attach(HttpURLConnection connection) throws IOException {
        this.connection = connection;
        if (cancelled) { connection.disconnect(); throw new IOException("已停止生成"); }
    }

    void cancel() {
        cancelled = true;
        HttpURLConnection active = connection;
        if (active != null) active.disconnect();
    }

    boolean isCancelled() { return cancelled; }
    void detach() { connection = null; }
}
