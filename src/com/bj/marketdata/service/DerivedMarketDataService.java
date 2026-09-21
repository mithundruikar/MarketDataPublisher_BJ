package com.bj.marketdata.service;

import com.bj.marketdata.entity.MarketDataRawUpdate;
import com.bj.marketdata.source.MarketDataListener;

import java.util.Map;
import java.util.Optional;

/**
 * Holds and serves derived market-data state.
 * Implementation intentionally deferred.
 */
public class DerivedMarketDataService implements MarketDataListener {
    public void applyUpdate(MarketDataRawUpdate update) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void onUpdate(MarketDataRawUpdate update) {
        applyUpdate(update);
    }

    public Optional<DerivedMarketData> getState(String instrument) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public Map<String, DerivedMarketData> snapshot() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public long rejectedUpdateCount() {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
