package com.qingcheng.ordering;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Submission failure is distinct from a request which reached the network. */
final class TaskSubmission {
    interface Work { void run() throws Exception; }

    static final class NotStartedException extends Exception {
        NotStartedException(RejectedExecutionException cause) {
            super("当前操作较多，请稍后重试；本次请求尚未发送", cause);
        }
    }

    static void execute(Executor executor, Work work, Consumer<Exception> failed, Runnable finished) {
        AtomicBoolean started = new AtomicBoolean();
        Runnable task = () -> {
            started.set(true);
            try {
                work.run();
            } catch (Exception error) {
                failed.accept(error);
            } finally {
                finished.run();
            }
        };
        try {
            executor.execute(task);
        } catch (RejectedExecutionException rejected) {
            // A direct executor may propagate a callback exception after work has already run.
            if (started.get()) throw rejected;
            try {
                failed.accept(new NotStartedException(rejected));
            } finally {
                finished.run();
            }
        }
    }

    private TaskSubmission() { }
}
