package com.bj.marketdata.service;

public record RawMarketUpdate(
        long timestampMs,
        String instrument,
        String inputType,
        double value
) {
}
