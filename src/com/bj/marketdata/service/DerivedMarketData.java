package com.bj.marketdata.service;

public record DerivedMarketData(
        long timestampMillis,
        String instrument,
        double baseRate,
        double spread,
        double adjustment,
        double derivedValue
) {
}
