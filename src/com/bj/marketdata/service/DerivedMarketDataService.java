package com.bj.marketdata.service;

import com.bj.marketdata.entity.MarketDataRawUpdate;
import com.bj.marketdata.source.MarketDataListener;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;

/**
 * Holds and serves derived market-data state.
 * Implementation intentionally deferred.
 */
public final class DerivedMarketDataService implements MarketDataListener {
    private static final int DEFAULT_EXPECTED_DISTINCT_INSTRUMENTS = 256;
    private static final double LOAD_FACTOR = 0.70d;

    private final String[] instrumentsBySlot;
    private final MarketDataUpdate[] updatesBySlot;
    private final int slotMask;
    private final int maxDistinctInstruments;
    private int distinctInstruments;
    private long rejectedUpdateCount;

    public DerivedMarketDataService() {
        this(DEFAULT_EXPECTED_DISTINCT_INSTRUMENTS);
    }

    public DerivedMarketDataService(final int expectedDistinctInstruments) {
        final int tableCapacity = tableCapacityFor(expectedDistinctInstruments);
        this.instrumentsBySlot = new String[tableCapacity];
        this.updatesBySlot = new MarketDataUpdate[tableCapacity];
        this.slotMask = tableCapacity - 1;
        this.maxDistinctInstruments = Math.max(1, (int) Math.floor(tableCapacity * LOAD_FACTOR));
        for (int i = 0; i < tableCapacity; i++) {
            updatesBySlot[i] = new MarketDataUpdate();
        }
    }

    public void applyUpdate(final MarketDataRawUpdate update) {
        Objects.requireNonNull(update, "update");
        if (update.instrument() == null || update.instrument().isBlank() || update.inputType() == null) {
            rejectedUpdateCount++;
            return;
        }
        if (!isSupportedInputType(update.inputType())) {
            rejectedUpdateCount++;
            return;
        }

        final int slot = findOrAllocateSlot(update.instrument());
        final MarketDataUpdate conflated = updatesBySlot[slot];
        if (!conflated.applyRawValue(update.inputType(), update.value(), update.updateTimeMillis())) {
            rejectedUpdateCount++;
        }
    }

    @Override
    public void onUpdate(final MarketDataRawUpdate update) {
        applyUpdate(update);
    }

    public Optional<MarketDataUpdate> getState(final String instrument) {
        if (instrument == null || instrument.isBlank()) {
            return Optional.empty();
        }
        final int slot = findExistingSlot(instrument);
        if (slot < 0) {
            return Optional.empty();
        }
        return Optional.of(updatesBySlot[slot]);
    }

    public Map<String, MarketDataUpdate> snapshot() {
        final Map<String, MarketDataUpdate> snapshot = new HashMap<>(distinctInstruments);
        for (int i = 0; i < instrumentsBySlot.length; i++) {
            final String instrument = instrumentsBySlot[i];
            if (instrument != null) {
                snapshot.put(instrument, updatesBySlot[i]);
            }
        }
        return snapshot;
    }

    public long rejectedUpdateCount() {
        return rejectedUpdateCount;
    }

    private int findOrAllocateSlot(final String instrument) {
        int idx = mixHash(instrument.hashCode()) & slotMask;
        while (true) {
            final String existing = instrumentsBySlot[idx];
            if (existing == null) {
                if (distinctInstruments >= maxDistinctInstruments) {
                    throw new IllegalStateException(
                            "Distinct instrument capacity exceeded: " + maxDistinctInstruments + ", instrument=" + instrument);
                }
                instrumentsBySlot[idx] = instrument;
                updatesBySlot[idx].initializeInstrument(instrument);
                distinctInstruments++;
                return idx;
            }
            if (existing.equals(instrument)) {
                return idx;
            }
            idx = (idx + 1) & slotMask;
        }
    }

    private int findExistingSlot(final String instrument) {
        int idx = mixHash(instrument.hashCode()) & slotMask;
        while (true) {
            final String existing = instrumentsBySlot[idx];
            if (existing == null) {
                return -1;
            }
            if (existing.equals(instrument)) {
                return idx;
            }
            idx = (idx + 1) & slotMask;
        }
    }

    private static int tableCapacityFor(final int expectedDistinctInstruments) {
        if (expectedDistinctInstruments < 1) {
            throw new IllegalArgumentException("expectedDistinctInstruments must be > 0");
        }
        final int requiredCapacity = (int) Math.ceil(expectedDistinctInstruments / LOAD_FACTOR);
        int capacity = 1;
        while (capacity < requiredCapacity) {
            capacity <<= 1;
            if (capacity <= 0) {
                throw new IllegalArgumentException("expectedDistinctInstruments too large");
            }
        }
        return capacity;
    }

    private static int mixHash(final int hash) {
        return hash ^ (hash >>> 16);
    }

    private static boolean isSupportedInputType(final String inputType) {
        return switch (inputType) {
            case "base_rate", "spread", "adjustment" -> true;
            default -> false;
        };
    }
}
