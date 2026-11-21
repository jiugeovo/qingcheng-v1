package com.example.ordering.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Fixed, length-delimited encoding; item order remains significant because it is visible in the response. */
public final class OrderRequestFingerprint {
    private OrderRequestFingerprint() { }

    public record Line(long itemId, int quantity) { }

    public static String hash(List<Line> lines, String remark) {
        String normalizedRemark = remark == null ? "" : remark.trim();
        StringBuilder canonical = new StringBuilder("order-v1\n").append(lines.size()).append('\n');
        for (Line line : lines) {
            canonical.append(line.itemId()).append(':').append(line.quantity()).append('\n');
        }
        canonical.append(normalizedRemark.length()).append(':').append(normalizedRemark);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
