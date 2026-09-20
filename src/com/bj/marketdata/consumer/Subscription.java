package com.bj.marketdata.consumer;

import java.util.Set;

public record Subscription(
        String clientId,
        Set<String> instruments
) {
}
