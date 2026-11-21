package com.example.ordering.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/** Resolves the caller address without trusting spoofable forwarding headers. */
@Component
public final class ClientIpResolver {

    private final List<Network> trustedProxies;

    public ClientIpResolver(ClientIpProperties properties) {
        this.trustedProxies = parseNetworks(properties.getTrustedProxies());
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = normalize(request.getRemoteAddr());
        if (!isTrustedProxy(remoteAddress)) {
            return remoteAddress;
        }

        String forwarded = firstValidHeaderIp(request.getHeader("X-Forwarded-For"));
        if (forwarded != null) {
            return forwarded;
        }
        String realIp = firstValidHeaderIp(request.getHeader("X-Real-IP"));
        if (realIp != null) {
            return realIp;
        }
        String cloudflareIp = firstValidHeaderIp(request.getHeader("CF-Connecting-IP"));
        return cloudflareIp != null ? cloudflareIp : remoteAddress;
    }

    private boolean isTrustedProxy(String address) {
        if (address == null || address.isBlank()) {
            return false;
        }
        try {
            InetAddress parsed = InetAddress.getByName(address);
            return trustedProxies.stream().anyMatch(network -> network.contains(parsed));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String firstValidHeaderIp(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        for (String part : header.split(",")) {
            String candidate = normalize(part);
            if (candidate == null || "unknown".equalsIgnoreCase(candidate)) {
                continue;
            }
            if (!isIpLiteral(candidate)) {
                continue;
            }
            try {
                return InetAddress.getByName(candidate).getHostAddress();
            } catch (Exception ignored) {
                // Ignore malformed entries and continue with the next hop.
            }
        }
        return null;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? "" : normalized;
    }

    private static boolean isIpLiteral(String value) {
        // Do not resolve attacker-controlled host names from forwarding headers.
        return value.matches("[0-9A-Fa-f:.]+");
    }

    private static List<Network> parseNetworks(List<String> values) {
        List<Network> networks = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                if (!isNetworkLiteral(value.trim().split("/", 2)[0])) {
                    continue;
                }
                networks.add(Network.parse(value.trim()));
            } catch (Exception ignored) {
                // Invalid configuration must not disable the application.
            }
        }
        return List.copyOf(networks);
    }

    private static boolean isNetworkLiteral(String value) {
        return isIpLiteral(value);
    }

    private record Network(byte[] address, int prefixLength) {
        static Network parse(String value) throws Exception {
            String[] parts = value.split("/", 2);
            InetAddress address = InetAddress.getByName(parts[0]);
            int max = address.getAddress().length * 8;
            int prefix = parts.length == 1 ? max : Integer.parseInt(parts[1]);
            if (prefix < 0 || prefix > max) {
                throw new IllegalArgumentException("Invalid network prefix");
            }
            return new Network(address.getAddress(), prefix);
        }

        boolean contains(InetAddress candidate) {
            byte[] candidateBytes = candidate.getAddress();
            if (candidateBytes.length != address.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            for (int index = 0; index < fullBytes; index++) {
                if (candidateBytes[index] != address[index]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits);
            return (candidateBytes[fullBytes] & mask) == (address[fullBytes] & mask);
        }
    }
}
