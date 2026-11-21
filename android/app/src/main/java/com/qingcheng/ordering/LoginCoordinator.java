package com.qingcheng.ordering;

import java.util.Objects;

final class LoginCoordinator {
    static final String REQUEST_SLOT = "login-submit";

    enum Kind {
        LOGIN,
        REGISTER
    }

    record Request(ScreenGeneration.RequestToken request, Kind kind) {
        Request {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(kind, "kind");
        }
    }

    private final ScreenGeneration generations;
    private Request active;

    LoginCoordinator(ScreenGeneration generations) {
        this.generations = Objects.requireNonNull(generations, "generations");
    }

    synchronized Request begin(ScreenGeneration.RequestToken request, Kind kind) {
        if (!isEligible(request) || kind == null) {
            return null;
        }
        active = new Request(request, kind);
        return active;
    }

    synchronized boolean isCurrent(Request request) {
        return request != null && request.equals(active) && isEligible(request.request());
    }

    synchronized boolean finish(Request request) {
        if (!isCurrent(request)) {
            return false;
        }
        active = null;
        return true;
    }

    private boolean isEligible(ScreenGeneration.RequestToken request) {
        return request != null
                && request.screen().route() == ScreenRoute.LOGIN
                && REQUEST_SLOT.equals(request.slot())
                && generations.isCurrent(request);
    }
}
