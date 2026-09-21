package com.bj.marketdata.entity;

public record MarketDataRawUpdate(
        long timestampMs,
        String instrument,
        String inputType,
        double value
) {
}
