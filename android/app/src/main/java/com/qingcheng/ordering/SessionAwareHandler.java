package com.qingcheng.ordering;

import android.os.Handler;
import android.os.Looper;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Drops queued worker callbacks after their account session or owner becomes stale. */
final class SessionAwareHandler {
    private final Handler handler;
    private final Supplier<AccountScope> currentScope;
    private final BooleanSupplier ownerAlive;

    SessionAwareHandler(Looper looper, Supplier<AccountScope> currentScope, BooleanSupplier ownerAlive) {
        handler = new Handler(looper);
        this.currentScope = Objects.requireNonNull(currentScope, "currentScope");
        this.ownerAlive = Objects.requireNonNull(ownerAlive, "ownerAlive");
    }

    Handler handler() { return handler; }

    boolean post(Runnable action) {
        AccountScope capturedScope = TaskRunner.currentScope();
        return handler.post(() -> {
            if (!shouldDeliver(capturedScope, currentScope.get(), ownerAlive.getAsBoolean())) return;
            action.run();
        });
    }

    static boolean shouldDeliver(AccountScope captured, AccountScope current, boolean ownerAlive) {
        return ownerAlive && (captured == null || captured.equals(current));
    }
}
