package com.bj.marketdata.service;

public record DerivedMarketData(
        String instrument,
        double baseRate,
        double spread,
        double adjustment,
        double derivedValue
) {
}
