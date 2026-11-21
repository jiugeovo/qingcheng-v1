package com.qingcheng.ordering;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns cancellable requests for one account/session generation. */
final class TaskRunner {
    private static final ThreadLocal<AccountScope> ACTIVE_SCOPE = new ThreadLocal<>();
    private volatile Executor executor;
    private final Set<FutureTask<?>> tasks = ConcurrentHashMap.newKeySet();
    private volatile long generation;
    private volatile boolean closed;
    private volatile String currentToken = "";
    private volatile AccountScope currentScope;
    private long localAccountGeneration;

    static AccountScope currentScope() { return ACTIVE_SCOPE.get(); }

    TaskRunner(Executor executor) { this.executor = executor; }

    synchronized void useExecutor(Executor next) {
        if (tasks.isEmpty() && next != null) executor = next;
    }

    synchronized AccountScope scope(String token) {
        String value = token == null ? "" : token;
        if (closed) throw new IllegalStateException("TaskRunner is closed");
        if (!value.equals(currentToken) || currentScope == null) {
            currentToken = value;
            activate(new AccountScope(value, ++localAccountGeneration));
        }
        return currentScope;
    }

    synchronized AccountScope scope(AccountScope accountScope) {
        if (closed) throw new IllegalStateException("TaskRunner is closed");
        if (accountScope == null) throw new IllegalArgumentException("accountScope is required");
        if (!accountScope.equals(currentScope)) {
            currentToken = accountScope.token();
            activate(accountScope);
        }
        return currentScope;
    }

    <T> void submit(AccountScope scope, Supplier<String> currentToken, Work<T> work,
                    Consumer<T> success, Consumer<Exception> failure, Runnable finished) {
        submitForSession(scope,
                () -> scope != null && scope.token().equals(normalizeToken(currentToken.get())),
                work, success, failure, finished);
    }

    <T> void submitForSession(AccountScope scope, BooleanSupplier accountIsCurrent, Work<T> work,
                              Consumer<T> success, Consumer<Exception> failure, Runnable finished) {
        long taskGeneration = generation;
        AtomicBoolean started = new AtomicBoolean();
        AtomicBoolean finishedOnce = new AtomicBoolean();
        Runnable finish = () -> { if (finishedOnce.compareAndSet(false, true)) finished.run(); };
        FutureTask<Void> task = new FutureTask<>(() -> {
            started.set(true);
            AccountScope previousScope = ACTIVE_SCOPE.get();
            if (scope == null) ACTIVE_SCOPE.remove();
            else ACTIVE_SCOPE.set(scope);
            try {
                if (!isCurrent(scope, taskGeneration, accountIsCurrent)) return null;
                T result = work.run();
                if (isCurrent(scope, taskGeneration, accountIsCurrent)) success.accept(result);
            } catch (Exception error) {
                if (isCurrent(scope, taskGeneration, accountIsCurrent)) failure.accept(error);
            } finally {
                if (previousScope == null) ACTIVE_SCOPE.remove();
                else ACTIVE_SCOPE.set(previousScope);
                finish.run();
            }
            return null;
        }) {
            @Override protected void done() { tasks.remove(this); finish.run(); }
        };
        if (closed || !isCurrent(scope, taskGeneration, accountIsCurrent)) return;
        tasks.add(task);
        try { executor.execute(task); }
        catch (RuntimeException error) {
            tasks.remove(task);
            if (started.get()) throw error;
            try {
                if (isCurrent(scope, taskGeneration, accountIsCurrent)) failure.accept(new TaskSubmission.NotStartedException(
                        error instanceof java.util.concurrent.RejectedExecutionException rejected
                                ? rejected : new java.util.concurrent.RejectedExecutionException(error)));
            } finally {
                // Cancelling invokes done()/finished. Deliver failure first so callers can present
                // the retry state while their mutation/session lease is still current.
                task.cancel(false);
            }
        }
    }

    synchronized void invalidate() { generation++; currentScope = null; cancelAll(); }
    synchronized void close() { closed = true; generation++; cancelAll(); }
    private void cancelAll() { for (FutureTask<?> task : tasks) task.cancel(true); tasks.clear(); }

    private synchronized void activate(AccountScope scope) {
        currentScope = scope;
        generation++;
        cancelAll();
    }

    private boolean isCurrent(AccountScope scope, long taskGeneration, BooleanSupplier accountIsCurrent) {
        return !closed && generation == taskGeneration && scope != null && scope.equals(currentScope)
                && accountIsCurrent.getAsBoolean();
    }

    private static String normalizeToken(String token) { return token == null ? "" : token; }

    interface Work<T> { T run() throws Exception; }
}
