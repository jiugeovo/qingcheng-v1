package com.example.ordering.capability;

import com.example.ordering.couple.CoupleAccess;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.function.Predicate;
import org.springframework.security.access.AccessDeniedException;
import com.example.ordering.user.UserRole;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CapabilityRegistry {
    private final List<Capability> capabilities;
    private final Map<String, Capability> byId;
    private final Map<String, CapabilityExecutor> executors;
    private CoupleAccess couple = CoupleAccess.legacy();

    @Autowired(required = false)
    void configureCouple(CoupleAccess couple) {
        this.couple = couple;
    }

    public CapabilityRegistry(List<Capability> capabilities, List<CapabilityExecutor> executors) {
        if (capabilities == null || executors == null) {
            throw new IllegalStateException("Capability registry dependencies are missing");
        }
        List<Capability> discovered = new java.util.ArrayList<>(capabilities);
        validateCapabilities(discovered);
        this.capabilities = discovered.stream()
                .map(CapabilityRegistry::snapshot)
                .sorted(Comparator.comparing(Capability::id))
                .toList();
        this.byId = this.capabilities.stream().collect(Collectors.toUnmodifiableMap(Capability::id, Function.identity()));
        validateExecutors(executors);
        this.executors = executors.stream().collect(Collectors.toUnmodifiableMap(CapabilityExecutor::capabilityId, Function.identity()));
        this.capabilities.stream().filter(Capability::enabled).forEach(capability -> {
            if (!this.executors.containsKey(capability.id())) {
                throw new IllegalStateException("Enabled capability has no executor: " + capability.id());
            }
        });
    }

    private static Capability snapshot(Capability capability) {
        return new RegisteredCapability(capability.id(), capability.name(), capability.description(),
                capability.category(), capability.route(), capability.enabled(),
                CapabilitySchemas.freeze(capability.inputSchema()), Set.copyOf(capability.visibleTo()),
                capability::normalizeInput);
    }

    public List<Capability> all() { return capabilities; }

    public List<Map<String, Object>> catalog() {
        return catalog(null);
    }

    public List<Map<String, Object>> catalog(UserRole role) {
        return filteredCatalog(capability -> role == null || capability.visibleTo().contains(role));
    }

    public List<Map<String, Object>> catalogFor(CapabilityExecutionContext context) {
        return filteredCatalog(capability -> permits(capability, context));
    }

    private List<Map<String, Object>> filteredCatalog(Predicate<Capability> visible) {
        return capabilities.stream().filter(Capability::enabled).filter(visible)
                .map(capability -> Map.of(
                "id", capability.id(),
                "name", capability.name(),
                "description", capability.description(),
                "category", capability.category(),
                "route", capability.route(),
                "executable", executors.containsKey(capability.id()),
                "inputSchema", capability.inputSchema()
        )).toList();
    }

    private boolean permits(Capability capability, CapabilityExecutionContext context) {
        if (!couple.enabled()) return capability.visibleTo().contains(context.role());
        if (!couple.isMember(context.toUserAccount())) return false;
        if (capability.id().equals(ImageUploadCapability.ID)) return true;
        if (!capability.visibleTo().contains(context.role())) return false;
        return switch (capability.id()) {
            case OrderCreateCapability.ID -> context.userId() == couple.memberId(CoupleAccess.Member.JIUGE);
            case ShopOrderCreateCapability.ID -> context.userId() == couple.memberId(CoupleAccess.Member.BAOBAO);
            default -> true;
        };
    }

    private void validateCapabilities(List<Capability> values) {
        for (Capability capability : values) {
            if (capability == null) {
                throw new IllegalStateException("Capability must not be null");
            }
            if (capability.id() == null || capability.id().isBlank()
                    || capability.name() == null || capability.name().isBlank()
                    || capability.description() == null || capability.description().isBlank()
                    || capability.category() == null || capability.category().isBlank()
                    || capability.route() == null || capability.route().isBlank()) {
                throw new IllegalStateException("Capability metadata is incomplete");
            }
            if (capability.visibleTo() == null || capability.visibleTo().isEmpty()
                    || capability.visibleTo().stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalStateException("Capability visibility is incomplete: " + capability.id());
            }
            if (capability.inputSchema() == null) {
                throw new IllegalStateException("Capability input schema is missing: " + capability.id());
            }
        }
        if (values.stream().map(Capability::id).distinct().count() != values.size()) {
            throw new IllegalStateException("Duplicate capability id");
        }
    }

    private void validateExecutors(List<CapabilityExecutor> values) {
        for (CapabilityExecutor executor : values) {
            if (executor == null || executor.capabilityId() == null || executor.capabilityId().isBlank()) {
                throw new IllegalStateException("Capability executor id is incomplete");
            }
            if (!byId.containsKey(executor.capabilityId())) {
                throw new IllegalStateException("Executor references unknown capability: " + executor.capabilityId());
            }
        }
        if (values.stream().map(CapabilityExecutor::capabilityId).distinct().count() != values.size()) {
            throw new IllegalStateException("Duplicate capability executor id");
        }
    }

    public Capability require(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Unknown capability: " + id);
        }
        Capability capability = byId.get(id);
        if (capability == null) throw new IllegalArgumentException("Unknown capability: " + id);
        if (!capability.enabled()) throw new IllegalArgumentException("Disabled capability: " + id);
        return capability;
    }

    public Map<String, Object> execute(String id, Map<String, Object> input, CapabilityExecutionContext context) {
        Capability capability = require(id);
        if (context == null) {
            throw new IllegalArgumentException("用户上下文不能为空");
        }
        if (!permits(capability, context)) {
            throw new AccessDeniedException("没有权限使用该能力");
        }
        Map<String, Object> normalized = CapabilitySchemas.freezeInput(capability.normalizeInput(input));
        CapabilitySchemas.validate(capability.inputSchema(), normalized);
        CapabilityExecutor executor = executors.get(id);
        if (executor == null) throw new IllegalArgumentException("能力暂不可用: " + id);
        return executor.execute(normalized, context);
    }

    /** Snapshots are immutable even if a future component returns mutable metadata collections. */
    private record RegisteredCapability(String id, String name, String description, String category, String route,
                                        boolean enabled, Map<String, Object> inputSchema, Set<UserRole> visibleTo,
                                        Function<Map<String, Object>, Map<String, Object>> normalizer) implements Capability {
        @Override
        public Map<String, Object> normalizeInput(Map<String, Object> input) {
            return normalizer.apply(input);
        }
    }
}
