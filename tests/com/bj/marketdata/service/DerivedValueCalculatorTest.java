package com.bj.marketdata.service;

import com.bj.marketdata.source.InstrumentUpdateType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DerivedValueCalculatorTest {
    @Test
    void shouldReturnInvalidSentinelWhenBaseRateIsMissing() {
        final MarketDataUpdate update = new MarketDataUpdate();
        update.initializeInstrument("ALPHA");
        update.applyRawValue(InstrumentUpdateType.SPREAD, 250L, 1_000L);

        final DerivedValueCalculator calculator = new DerivedValueCalculator();
        assertEquals(DerivedValueCalculator.INVALID_DERIVED_VALUE, calculator.derive(update));
    }

    @Test
    void shouldReturnBasePlusSpreadPlusAdjustmentWhenBaseRatePresent() {
        final MarketDataUpdate update = new MarketDataUpdate();
        update.initializeInstrument("BRAVO");
        update.applyRawValue(InstrumentUpdateType.BASE_RATE, 10_000L, 1_000L);
        update.applyRawValue(InstrumentUpdateType.SPREAD, 200L, 1_010L);
        update.applyRawValue(InstrumentUpdateType.ADJUSTMENT, -50L, 1_020L);

        final DerivedValueCalculator calculator = new DerivedValueCalculator();
        assertEquals(10_150L, calculator.derive(update));
    }
}
