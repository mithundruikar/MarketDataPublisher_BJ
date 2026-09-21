package com.bj.marketdata.source;

public record MarketDataRawUpdate(
        long sequence,
        long updateTimeMillis,
        String source,
        String instrument,
        InstrumentUpdateType inputType,
        long value
) {
}
