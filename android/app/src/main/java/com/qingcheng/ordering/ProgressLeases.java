package com.qingcheng.ordering;

import java.util.Objects;
import java.util.function.Consumer;

final class ProgressLeases {
    final class Lease implements AutoCloseable {
        private final long generation;
        private boolean closed;

        private Lease(long generation) {
            this.generation = generation;
        }

        @Override
        public void close() {
            synchronized (ProgressLeases.this) {
                if (closed) {
                    return;
                }
                closed = true;
                closeLease(generation);
            }
        }
    }

    private final Consumer<Boolean> visibility;
    private int activeCount;
    private long generation;

    ProgressLeases(Consumer<Boolean> visibility) {
        this.visibility = Objects.requireNonNull(visibility, "visibility");
    }

    synchronized Lease open() {
        if (activeCount == 0) {
            visibility.accept(true);
        }
        activeCount++;
        return new Lease(generation);
    }

    synchronized int activeCount() {
        return activeCount;
    }

    synchronized void reset() {
        boolean wasVisible = activeCount > 0;
        activeCount = 0;
        generation++;
        if (wasVisible) {
            visibility.accept(false);
        }
    }

    private void closeLease(long leaseGeneration) {
        if (leaseGeneration != generation || activeCount == 0) {
            return;
        }
        activeCount--;
        if (activeCount == 0) {
            visibility.accept(false);
        }
    }
}
