package com.qingcheng.ordering;

import android.os.Process;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class WorkerPools {
    private WorkerPools() {
    }

    static ExecutorService fixed(String role, int workerCount, int queueCapacity) {
        if (workerCount <= 0) {
            throw new IllegalArgumentException("workerCount must be positive");
        }
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be positive");
        }
        String safeRole = role == null || role.trim().isEmpty() ? "worker" : role.trim();
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = task -> {
            Thread thread = new Thread(() -> {
                try {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                } catch (RuntimeException ignored) {
                    // JVM tests and a few vendor builds may reject Linux thread priorities.
                }
                task.run();
            }, "qingcheng-" + safeRole + "-" + sequence.incrementAndGet());
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        };
        return new ThreadPoolExecutor(
                workerCount,
                workerCount,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                factory,
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
