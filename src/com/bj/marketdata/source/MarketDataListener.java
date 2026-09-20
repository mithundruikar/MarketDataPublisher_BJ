package com.bj.marketdata.source;

import com.bj.marketdata.service.RawMarketUpdate;

public interface MarketDataListener {
    void onUpdate(RawMarketUpdate update);

    default void onRejected(String rawRecord, String reason) {
    }
}
