package com.qingcheng.ordering;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Maps discovered capability IDs to existing screens without embedding UI in the catalog. */
final class CapabilityActionRouter {
    private CapabilityActionRouter() { }

    static Map<String, Runnable> routes(Runnable weather, Runnable friends, Runnable ai, Runnable wallet) {
        Map<String, Runnable> routes = new LinkedHashMap<>();
        routes.put("weather.current", Objects.requireNonNull(weather));
        routes.put("friend.message", Objects.requireNonNull(friends));
        routes.put("ai.chat", Objects.requireNonNull(ai));
        routes.put("wallet.check-in", Objects.requireNonNull(wallet));
        return Collections.unmodifiableMap(routes);
    }

    static Map<String, Runnable> routes(Runnable weather, Runnable friends, Runnable ai, Runnable wallet,
                                        Runnable anniversaries) {
        Map<String, Runnable> routes = new LinkedHashMap<>(routes(weather, friends, ai, wallet));
        if (anniversaries != null) {
            routes.put("anniversary.list", anniversaries);
        }
        return Collections.unmodifiableMap(routes);
    }

    static boolean isRoutable(String capabilityId) {
        if (capabilityId == null) return false;
        return switch (capabilityId) {
            case "weather.current", "friend.message", "ai.chat", "wallet.check-in", "anniversary.list" -> true;
            default -> false;
        };
    }

    static Runnable actionFor(Capability capability, Map<String, Runnable> routes) {
        if (capability == null || !capability.executable() || !isRoutable(capability.id())) {
            return null;
        }
        return routes.get(capability.id());
    }
}
