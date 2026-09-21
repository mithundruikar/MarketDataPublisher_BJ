package com.bj.marketdata.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InstrumentUpdateTypeTest {
    @Test
    void shouldParseKnownWireValues() {
        assertEquals(InstrumentUpdateType.BASE_RATE, InstrumentUpdateType.fromWireValue("base_rate"));
        assertEquals(InstrumentUpdateType.SPREAD, InstrumentUpdateType.fromWireValue("spread"));
        assertEquals(InstrumentUpdateType.ADJUSTMENT, InstrumentUpdateType.fromWireValue("adjustment"));
    }

    @Test
    void shouldMapUnknownWireValueToUnknown() {
        assertEquals(InstrumentUpdateType.UNKNOWN, InstrumentUpdateType.fromWireValue("unsupported"));
    }
}
