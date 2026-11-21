package com.example.ordering.capability;

import java.util.Map;

/** Executes one capability with already authenticated user context. */
public interface CapabilityExecutor {
    String capabilityId();
    Map<String, Object> execute(Map<String, Object> input, CapabilityExecutionContext context);
}
