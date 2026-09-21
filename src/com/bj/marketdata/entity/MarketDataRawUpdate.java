package com.bj.marketdata.entity;

public record MarketDataRawUpdate(
        long sequence,
        long updateTimeMillis,
        String source,
        String instrument,
        String inputType,
        double value
) {
}
