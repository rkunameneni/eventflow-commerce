package io.github.rkunameneni.eventflow.order.application;

import io.github.rkunameneni.eventflow.order.domain.Order;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SHA-256 over a canonical form of an order request. Prices are normalised ({@code 10.0} equals
 * {@code 10.00}) so that semantically identical retries are recognised as such.
 */
public final class RequestFingerprint {

    private RequestFingerprint() {
    }

    public static String of(String currency, List<Order.LineItem> lines) {
        String canonical = currency + "|" + lines.stream()
                .map(line -> line.sku() + ":" + line.quantity() + ":"
                        + line.unitPrice().stripTrailingZeros().toPlainString())
                .collect(Collectors.joining(","));
        return HexFormat.of().formatHex(sha256(canonical));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JVM", e);
        }
    }
}
