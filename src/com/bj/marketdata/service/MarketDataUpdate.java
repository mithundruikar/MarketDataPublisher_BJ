package com.bj.marketdata.service;

import com.bj.marketdata.entity.InstrumentUpdateType;

public final class MarketDataUpdate {
    private String instrument;
    private double baseRate;
    private double spread;
    private double adjustment;
    private long lastUpdatedMillis;
    private long version;

    public String instrument() {
        return instrument;
    }

    public double baseRate() {
        return baseRate;
    }

    public double spread() {
        return spread;
    }

    public double adjustment() {
        return adjustment;
    }

    public long lastUpdatedMillis() {
        return lastUpdatedMillis;
    }

    public long version() {
        return version;
    }

    public double derivedValue() {
        return baseRate + spread + adjustment;
    }

    void initializeInstrument(final String instrument) {
        this.instrument = instrument;
    }

    boolean applyRawValue(final InstrumentUpdateType inputType, final double value, final long updatedTimeMillis) {
        switch (inputType) {
            case BASE_RATE -> baseRate = value;
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
