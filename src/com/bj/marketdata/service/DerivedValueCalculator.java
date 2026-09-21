package com.bj.marketdata.service;

import java.util.Objects;

public final class DerivedValueCalculator {
    public static final long INVALID_DERIVED_VALUE = -1L;

    public long derive(final MarketDataUpdate update) {
        Objects.requireNonNull(update, "update");
        if (!update.hasBaseRate()) {
            return INVALID_DERIVED_VALUE;
        }
        return update.baseRate() + update.spread() + update.adjustment();
    }
}
