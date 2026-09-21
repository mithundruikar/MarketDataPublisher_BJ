package com.bj.marketdata.service;

import com.bj.marketdata.source.InstrumentUpdateType;
import com.bj.marketdata.source.MarketDataRawUpdate;
import com.bj.marketdata.value.ScaledPrice;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DerivedMarketDataServiceTest {
    @Test
    void shouldConflateByInstrumentAndTrackVersion() {
        final DerivedMarketDataService service = new DerivedMarketDataService(16);

        service.applyUpdate(new MarketDataRawUpdate(1L, 1_000L, "file-A", "ALPHA", InstrumentUpdateType.BASE_RATE, scaled("4.0")));
        service.applyUpdate(new MarketDataRawUpdate(2L, 1_010L, "file-A", "ALPHA", InstrumentUpdateType.SPREAD, scaled("0.3")));
        service.applyUpdate(new MarketDataRawUpdate(3L, 1_020L, "file-A", "BRAVO", InstrumentUpdateType.BASE_RATE, scaled("3.5")));
        service.applyUpdate(new MarketDataRawUpdate(4L, 1_030L, "file-A", "ALPHA", InstrumentUpdateType.ADJUSTMENT, scaled("-0.1")));

        final MarketDataUpdate alpha = service.getState("ALPHA").orElseThrow();
        assertEquals(scaled("4.0"), alpha.baseRate(), "base rate conflation failed");
        assertEquals(scaled("0.3"), alpha.spread(), "spread conflation failed");
        assertEquals(scaled("-0.1"), alpha.adjustment(), "adjustment conflation failed");
        assertEquals(scaled("4.2"), alpha.derivedValue(), "derived value calculation failed");
        assertEquals(1_030L, alpha.lastUpdatedMillis(), "last update millis mismatch");
        assertEquals(3L, alpha.version(), "version should increment per ALPHA update");

        final MarketDataUpdate bravo = service.getState("BRAVO").orElseThrow();
        assertEquals(scaled("3.5"), bravo.baseRate(), "BRAVO base rate mismatch");
        assertEquals(1L, bravo.version(), "BRAVO version mismatch");

        assertEquals(2, service.snapshot().size(), "expected two instrument entries");
    }

    @Test
    void shouldRejectUnsupportedInputType() {
        final DerivedMarketDataService service = new DerivedMarketDataService(8);
        service.applyUpdate(new MarketDataRawUpdate(1L, 2_000L, "file-B", "CHARLIE", InstrumentUpdateType.UNKNOWN, scaled("9.9")));

        assertEquals(1L, service.rejectedUpdateCount(), "unsupported input type should increment rejection count");
        assertTrue(service.getState("CHARLIE").isEmpty(), "invalid update should not create state");
    }

    @Test
    void shouldNotifyDerivedMarketDataListenersWithDerivedValue() {
        final DerivedMarketDataService service = new DerivedMarketDataService(8);
        final DerivedValueCalculator derivedValueCalculator = new DerivedValueCalculator();
        final List<DerivedMarketData> receivedUpdates = new ArrayList<>();
        service.addDerivedMarketDataUpdateListener(receivedUpdates::add);

        service.applyUpdate(new MarketDataRawUpdate(1L, 3_000L, "file-C", "DELTA", InstrumentUpdateType.BASE_RATE, scaled("4.2")));
        service.applyUpdate(new MarketDataRawUpdate(2L, 3_010L, "file-C", "DELTA", InstrumentUpdateType.SPREAD, scaled("0.3")));
        service.applyUpdate(new MarketDataRawUpdate(3L, 3_020L, "file-C", "DELTA", InstrumentUpdateType.ADJUSTMENT, scaled("-0.1")));

        assertEquals(3, receivedUpdates.size(), "each valid update should produce a derived update callback");
        final DerivedMarketData latest = receivedUpdates.get(receivedUpdates.size() - 1);
        assertEquals(3_020L, latest.timestampMillis(), "timestamp should match last update millis");
        assertEquals("DELTA", latest.instrument(), "instrument mismatch");
        assertEquals(scaled("4.4"), latest.derivedValue(), "derived value mismatch");
        assertEquals(
                scaled("4.4"),
                derivedValueCalculator.derive(service.getState("DELTA").orElseThrow()),
                "calculator deriveValue formula mismatch"
        );
    }

    @Test
    void shouldNotPublishDerivedUpdatesWhenBaseRateIsMissing() {
        final DerivedMarketDataService service = new DerivedMarketDataService(8);
        final List<DerivedMarketData> receivedUpdates = new ArrayList<>();
        service.addDerivedMarketDataUpdateListener(receivedUpdates::add);

        service.applyUpdate(new MarketDataRawUpdate(1L, 4_000L, "file-D", "ECHO", InstrumentUpdateType.SPREAD, scaled("0.4")));
        service.applyUpdate(new MarketDataRawUpdate(2L, 4_010L, "file-D", "ECHO", InstrumentUpdateType.ADJUSTMENT, scaled("-0.2")));

        assertEquals(0, receivedUpdates.size(), "missing base rate should suppress derived publishing");
    }

    @Test
    void shouldNotPublishDerivedUpdateWhenDerivedValueIsInvalidSentinel() {
        final DerivedMarketDataService service = new DerivedMarketDataService(8);
        final List<DerivedMarketData> receivedUpdates = new ArrayList<>();
        service.addDerivedMarketDataUpdateListener(receivedUpdates::add);

        service.applyUpdate(new MarketDataRawUpdate(
                1L,
                5_000L,
                "file-E",
                "FOXTROT",
                InstrumentUpdateType.BASE_RATE,
                DerivedValueCalculator.INVALID_DERIVED_VALUE
        ));

        assertEquals(0, receivedUpdates.size(), "invalid derived sentinel should suppress derived publishing");
    }

    private static long scaled(final String value) {
        return ScaledPrice.parse(value);
    }
}
