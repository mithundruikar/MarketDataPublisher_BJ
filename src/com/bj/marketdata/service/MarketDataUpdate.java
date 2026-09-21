package com.bj.marketdata.service;

import com.bj.marketdata.source.InstrumentUpdateType;

public final class MarketDataUpdate {
    private String instrument;
    private long baseRate;
    private boolean hasBaseRate;
    private long spread;
    private long adjustment;
    private long lastUpdatedMillis;
    private long version;

    public String instrument() {
        return instrument;
    }

    public long baseRate() {
        return baseRate;
    }

    public boolean hasBaseRate() {
        return hasBaseRate;
    }

    public long spread() {
        return spread;
    }

    public long adjustment() {
        return adjustment;
    }

    public long lastUpdatedMillis() {
        return lastUpdatedMillis;
    }

    public long version() {
        return version;
    }

    public long derivedValue() {
        if (!hasBaseRate) {
            return DerivedValueCalculator.INVALID_DERIVED_VALUE;
        }
        return baseRate + spread + adjustment;
    }

    void initializeInstrument(final String instrument) {
        this.instrument = instrument;
    }

    boolean applyRawValue(final InstrumentUpdateType inputType, final long value, final long updatedTimeMillis) {
        switch (inputType) {
            case BASE_RATE -> {
                baseRate = value;
                hasBaseRate = true;
            }
            case SPREAD -> spread = value;
            case ADJUSTMENT -> adjustment = value;
            default -> {
                return false;
            }
        }
        lastUpdatedMillis = updatedTimeMillis;
        version++;
        return true;
    }
}
