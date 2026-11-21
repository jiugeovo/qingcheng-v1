package com.qingcheng.ordering;

import java.util.Objects;
import java.util.function.BooleanSupplier;

final class AsyncPresentationGate {
    private final ScreenGeneration generations;

    AsyncPresentationGate(ScreenGeneration generations) {
        this.generations = Objects.requireNonNull(generations, "generations");
    }

    boolean present(
            ScreenGeneration.ScreenToken screen,
            BooleanSupplier targetAvailable,
            Runnable render
    ) {
        return presentIfCurrent(generations.isCurrent(screen), targetAvailable, render);
    }

    boolean present(
            ScreenGeneration.RequestToken request,
            BooleanSupplier targetAvailable,
            Runnable render
    ) {
        return presentIfCurrent(generations.isCurrent(request), targetAvailable, render);
    }

    void invalidate() {
        generations.invalidate();
    }

    private static boolean presentIfCurrent(
            boolean current,
            BooleanSupplier targetAvailable,
            Runnable render
    ) {
        Objects.requireNonNull(targetAvailable, "targetAvailable");
        Objects.requireNonNull(render, "render");
        if (!current || !targetAvailable.getAsBoolean()) {
            return false;
        }
        render.run();
        return true;
    }
}
