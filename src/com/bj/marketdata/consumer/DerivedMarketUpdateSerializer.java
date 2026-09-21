package com.bj.marketdata.consumer;

import com.bj.marketdata.service.DerivedMarketData;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

final class DerivedMarketUpdateSerializer {
    byte[] serialize(final DerivedMarketData update) {
        final DerivedMarketData nonNullUpdate = Objects.requireNonNull(update, "update");
        final StringBuilder builder = new StringBuilder(nonNullUpdate.instrument().length() + 48);
        builder.append(nonNullUpdate.timestampMillis())
                .append(',')
                .append(nonNullUpdate.instrument())
                .append(',')
                .append(nonNullUpdate.derivedValue())
                .append('\n');
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }
}
