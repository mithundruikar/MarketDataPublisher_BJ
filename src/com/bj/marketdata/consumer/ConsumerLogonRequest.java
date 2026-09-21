package com.bj.marketdata.consumer;

import java.util.Objects;

public record ConsumerLogonRequest(String clientId) {
    public ConsumerLogonRequest {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId must not be blank");
        }
    }

    public static ConsumerLogonRequest fromWire(final String wireMessage) {
        Objects.requireNonNull(wireMessage, "wireMessage");
        return new ConsumerLogonRequest(wireMessage.trim());
    }
}
