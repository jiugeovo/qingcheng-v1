package com.qingcheng.ordering;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

final class ScreenGeneration {
    record ScreenToken(ScreenRoute route, long generation) {
        ScreenToken {
            Objects.requireNonNull(route, "route");
        }
    }

    record RequestToken(ScreenToken screen, String slot, long sequence) {
        RequestToken {
            Objects.requireNonNull(screen, "screen");
            slot = requireSlot(slot);
        }
    }

    private final Map<String, Long> currentRequests = new HashMap<>();
    private ScreenToken currentScreen;
    private long nextScreenGeneration;
    private long nextRequestSequence;

    synchronized ScreenToken begin(ScreenRoute route) {
        currentScreen = new ScreenToken(route, ++nextScreenGeneration);
        currentRequests.clear();
        return currentScreen;
    }

    synchronized RequestToken request(ScreenToken screen, String slot) {
        Objects.requireNonNull(screen, "screen");
        String normalizedSlot = requireSlot(slot);
        RequestToken token = new RequestToken(screen, normalizedSlot, ++nextRequestSequence);
        if (isCurrent(screen)) {
            currentRequests.put(normalizedSlot, token.sequence());
        }
        return token;
    }

    synchronized boolean isCurrent(ScreenToken screen) {
        return screen != null && screen.equals(currentScreen);
    }

    synchronized boolean isCurrent(RequestToken request) {
        if (request == null || !isCurrent(request.screen())) {
            return false;
        }
        return Objects.equals(currentRequests.get(request.slot()), request.sequence());
    }

    synchronized void invalidate() {
        currentScreen = null;
        currentRequests.clear();
    }

    private static String requireSlot(String slot) {
        if (slot == null || slot.trim().isEmpty()) {
            throw new IllegalArgumentException("slot must not be blank");
        }
        return slot.trim();
    }
}
