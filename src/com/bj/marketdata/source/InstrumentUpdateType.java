package com.bj.marketdata.source;

public enum InstrumentUpdateType {
    BASE_RATE("base_rate"),
    SPREAD("spread"),
    ADJUSTMENT("adjustment"),
    UNKNOWN("unknown");

    private final String wireValue;

    InstrumentUpdateType(final String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static InstrumentUpdateType fromWireValue(final String wireValue) {
        return switch (wireValue) {
            case "base_rate" -> BASE_RATE;
            case "spread" -> SPREAD;
            case "adjustment" -> ADJUSTMENT;
            default -> UNKNOWN;
        };
    }
}
