package com.parth.orderflow.order;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.stream.Collectors;

final class OrderRequestFingerprint {

    private OrderRequestFingerprint() {
    }

    static String from(CreateOrderRequest request) {
        var productIds = new HashSet<>();
        for (CreateOrderItemRequest item : request.items()) {
            if (!productIds.add(item.productId())) {
                throw new DuplicateOrderProductException(item.productId());
            }
        }

        String canonical = request.items().stream()
                .sorted((left, right) -> left.productId().compareTo(right.productId()))
                .map(item -> item.productId() + ":" + item.quantity())
                .collect(Collectors.joining("|"));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable.", impossible);
        }
    }
}
