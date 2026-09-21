package com.bj.marketdata.source;

public interface MarketDataListener {
    void onUpdate(MarketDataRawUpdate update);

    default void onRejected(String rawRecord, String reason) {
    }
}
