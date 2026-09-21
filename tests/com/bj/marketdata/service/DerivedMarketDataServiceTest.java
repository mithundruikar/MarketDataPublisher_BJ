package com.bj.marketdata.service;

import com.bj.marketdata.entity.MarketDataRawUpdate;

public final class DerivedMarketDataServiceTest {
    private DerivedMarketDataServiceTest() {
    }

    public static void main(String[] args) {
        shouldConflateByInstrumentAndTrackVersion();
        shouldRejectUnsupportedInputType();
    }

    private static void shouldConflateByInstrumentAndTrackVersion() {
        final DerivedMarketDataService service = new DerivedMarketDataService(16);

        service.applyUpdate(new MarketDataRawUpdate(1L, 1_000L, "file-A", "ALPHA", "base_rate", 4.0));
        service.applyUpdate(new MarketDataRawUpdate(2L, 1_010L, "file-A", "ALPHA", "spread", 0.3));
        service.applyUpdate(new MarketDataRawUpdate(3L, 1_020L, "file-A", "BRAVO", "base_rate", 3.5));
        service.applyUpdate(new MarketDataRawUpdate(4L, 1_030L, "file-A", "ALPHA", "adjustment", -0.1));

        final MarketDataUpdate alpha = service.getState("ALPHA").orElseThrow();
        assert closeTo(alpha.baseRate(), 4.0) : "base rate conflation failed";
        assert closeTo(alpha.spread(), 0.3) : "spread conflation failed";
        assert closeTo(alpha.adjustment(), -0.1) : "adjustment conflation failed";
        assert closeTo(alpha.derivedValue(), 4.2) : "derived value calculation failed";
        assert alpha.lastUpdatedMillis() == 1_030L : "last update millis mismatch";
        assert alpha.version() == 3L : "version should increment per ALPHA update";

        final MarketDataUpdate bravo = service.getState("BRAVO").orElseThrow();
        assert closeTo(bravo.baseRate(), 3.5) : "BRAVO base rate mismatch";
        assert bravo.version() == 1L : "BRAVO version mismatch";

        assert service.snapshot().size() == 2 : "expected two instrument entries";
    }

    private static void shouldRejectUnsupportedInputType() {
        final DerivedMarketDataService service = new DerivedMarketDataService(8);
        service.applyUpdate(new MarketDataRawUpdate(1L, 2_000L, "file-B", "CHARLIE", "invalid_type", 9.9));

        assert service.rejectedUpdateCount() == 1L : "unsupported input type should increment rejection count";
        assert service.getState("CHARLIE").isEmpty() : "invalid update should not create state";
    }

    private static boolean closeTo(final double actual, final double expected) {
        return Math.abs(actual - expected) < 1e-9;
    }
}
