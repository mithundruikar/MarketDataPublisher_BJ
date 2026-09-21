package com.bj.marketdata.service;

import com.bj.marketdata.entity.InstrumentUpdateType;
import com.bj.marketdata.entity.MarketDataRawUpdate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DerivedMarketDataServiceTest {
    @Test
    void shouldConflateByInstrumentAndTrackVersion() {
        final DerivedMarketDataService service = new DerivedMarketDataService(16);

        service.applyUpdate(new MarketDataRawUpdate(1L, 1_000L, "file-A", "ALPHA", InstrumentUpdateType.BASE_RATE, 4.0));
        service.applyUpdate(new MarketDataRawUpdate(2L, 1_010L, "file-A", "ALPHA", InstrumentUpdateType.SPREAD, 0.3));
        service.applyUpdate(new MarketDataRawUpdate(3L, 1_020L, "file-A", "BRAVO", InstrumentUpdateType.BASE_RATE, 3.5));
        service.applyUpdate(new MarketDataRawUpdate(4L, 1_030L, "file-A", "ALPHA", InstrumentUpdateType.ADJUSTMENT, -0.1));

        final MarketDataUpdate alpha = service.getState("ALPHA").orElseThrow();
        assertEquals(4.0, alpha.baseRate(), 1e-9, "base rate conflation failed");
        assertEquals(0.3, alpha.spread(), 1e-9, "spread conflation failed");
        assertEquals(-0.1, alpha.adjustment(), 1e-9, "adjustment conflation failed");
        assertEquals(4.2, alpha.derivedValue(), 1e-9, "derived value calculation failed");
        assertEquals(1_030L, alpha.lastUpdatedMillis(), "last update millis mismatch");
        assertEquals(3L, alpha.version(), "version should increment per ALPHA update");

        final MarketDataUpdate bravo = service.getState("BRAVO").orElseThrow();
        assertEquals(3.5, bravo.baseRate(), 1e-9, "BRAVO base rate mismatch");
        assertEquals(1L, bravo.version(), "BRAVO version mismatch");

        assertEquals(2, service.snapshot().size(), "expected two instrument entries");
    }

    @Test
    void shouldRejectUnsupportedInputType() {
        final DerivedMarketDataService service = new DerivedMarketDataService(8);
        service.applyUpdate(new MarketDataRawUpdate(1L, 2_000L, "file-B", "CHARLIE", InstrumentUpdateType.UNKNOWN, 9.9));

        assertEquals(1L, service.rejectedUpdateCount(), "unsupported input type should increment rejection count");
        assertTrue(service.getState("CHARLIE").isEmpty(), "invalid update should not create state");
    }

    @Test
    void shouldNotifyDerivedMarketDataListenersWithDerivedValue() {
        final DerivedMarketDataService service = new DerivedMarketDataService(8);
        final List<DerivedMarketData> receivedUpdates = new ArrayList<>();
        service.addDerivedMarketDataUpdateListener(receivedUpdates::add);

        service.applyUpdate(new MarketDataRawUpdate(1L, 3_000L, "file-C", "DELTA", InstrumentUpdateType.BASE_RATE, 4.2));
        service.applyUpdate(new MarketDataRawUpdate(2L, 3_010L, "file-C", "DELTA", InstrumentUpdateType.SPREAD, 0.3));
        service.applyUpdate(new MarketDataRawUpdate(3L, 3_020L, "file-C", "DELTA", InstrumentUpdateType.ADJUSTMENT, -0.1));

        assertEquals(3, receivedUpdates.size(), "each valid update should produce a derived update callback");
        final DerivedMarketData latest = receivedUpdates.get(receivedUpdates.size() - 1);
        assertEquals(3_020L, latest.timestampMillis(), "timestamp should match last update millis");
        assertEquals("DELTA", latest.instrument(), "instrument mismatch");
        assertEquals(4.4, latest.derivedValue(), 1e-9, "derived value mismatch");
        assertEquals(
                4.4,
                DerivedMarketDataService.deriveValue(latest.baseRate(), latest.spread(), latest.adjustment()),
                1e-9,
                "service deriveValue formula mismatch"
        );
    }
}
