package com.example.ordering.capability;

import com.example.ordering.wallet.WalletService;
import org.springframework.stereotype.Component;

import java.util.Map;

/** No caller-supplied user identifier may choose whose wallet is changed. */
@Component
public final class WalletCheckInExecutor implements CapabilityExecutor {
    private final WalletService service;

    public WalletCheckInExecutor(WalletService service) {
        this.service = service;
    }

    @Override
    public String capabilityId() {
        return "wallet.check-in";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> input, CapabilityExecutionContext context) {
        return Map.of("wallet", service.checkIn(context.toUserAccount()));
    }
}
