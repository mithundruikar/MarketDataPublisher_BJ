package com.bj.marketdata.service;

import java.util.Map;
import java.util.Optional;

/**
 * Holds and serves derived market-data state.
 * Implementation intentionally deferred.
 */
public class DerivedMarketDataService {
    public void applyUpdate(RawMarketUpdate update) {
        throw new UnsupportedOperationException("Not implemented yet");
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
