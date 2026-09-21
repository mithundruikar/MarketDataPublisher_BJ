package com.bj.marketdata.service;

public record DerivedMarketData(
        long timestampMillis,
        String instrument,
        long baseRate,
        long spread,
        long adjustment,
        long derivedValue
) {
}
