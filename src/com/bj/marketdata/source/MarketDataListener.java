package com.bj.marketdata.source;

import com.bj.marketdata.entity.MarketDataRawUpdate;

public interface MarketDataListener {
    void onUpdate(MarketDataRawUpdate update);

    default void onRejected(String rawRecord, String reason) {
    }
}
