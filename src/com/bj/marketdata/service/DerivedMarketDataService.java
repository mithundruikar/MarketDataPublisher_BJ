package com.bj.marketdata.service;

import com.bj.marketdata.source.InstrumentUpdateType;
import com.bj.marketdata.source.MarketDataRawUpdate;
import com.bj.marketdata.source.MarketDataListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Holds and serves derived market-data state.
 * Implementation intentionally deferred.
 */
public final class DerivedMarketDataService implements MarketDataListener {
    private static final int DEFAULT_EXPECTED_DISTINCT_INSTRUMENTS = 256;
    private static final int LOAD_FACTOR_NUMERATOR = 70;
    private static final int LOAD_FACTOR_DENOMINATOR = 100;
    private static final Logger LOGGER = Logger.getLogger(DerivedMarketDataService.class.getName());

    private final String[] instrumentsBySlot;
    private final MarketDataUpdate[] updatesBySlot;
    private final DerivedValueCalculator derivedValueCalculator;
    private final List<DerivedMarketDataUpdateListener> derivedUpdateListeners = new ArrayList<>(4);
    private final int slotMask;
    private final int maxDistinctInstruments;
    private int distinctInstruments;
    private long rejectedUpdateCount;

    public DerivedMarketDataService() {
        this(DEFAULT_EXPECTED_DISTINCT_INSTRUMENTS);
    }

    public DerivedMarketDataService(final int expectedDistinctInstruments) {
        this(expectedDistinctInstruments, new DerivedValueCalculator());
    }

    DerivedMarketDataService(final int expectedDistinctInstruments, final DerivedValueCalculator derivedValueCalculator) {
        final int tableCapacity = tableCapacityFor(expectedDistinctInstruments);
        this.instrumentsBySlot = new String[tableCapacity];
        this.updatesBySlot = new MarketDataUpdate[tableCapacity];
        this.derivedValueCalculator = Objects.requireNonNull(derivedValueCalculator, "derivedValueCalculator");
        this.slotMask = tableCapacity - 1;
        this.maxDistinctInstruments = Math.max(1, (tableCapacity * LOAD_FACTOR_NUMERATOR) / LOAD_FACTOR_DENOMINATOR);
        for (int i = 0; i < tableCapacity; i++) {
            updatesBySlot[i] = new MarketDataUpdate();
        }
        logInfo("Initialized with instrument cache slots=" + tableCapacity
                + ", maxDistinctInstruments=" + maxDistinctInstruments);
    }

    public void applyUpdate(final MarketDataRawUpdate update) {
        Objects.requireNonNull(update, "update");
        if (update.instrument() == null || update.instrument().isBlank() || update.inputType() == null) {
            rejectedUpdateCount++;
            return;
        }
        if (update.inputType() == InstrumentUpdateType.UNKNOWN) {
            rejectedUpdateCount++;
            return;
        }

        final int slot = findOrAllocateSlot(update.instrument());
        final MarketDataUpdate conflated = updatesBySlot[slot];
        if (!conflated.applyRawValue(update.inputType(), update.value(), update.updateTimeMillis())) {
            rejectedUpdateCount++;
            return;
        }
        if (derivedUpdateListeners.isEmpty()) {
            return;
        }
        final long derivedValue = derivedValueCalculator.derive(conflated);
        if (derivedValue == DerivedValueCalculator.INVALID_DERIVED_VALUE) {
            return;
        }

        final DerivedMarketData derivedMarketData = new DerivedMarketData(
                conflated.lastUpdatedMillis(),
                conflated.instrument(),
                conflated.baseRate(),
                conflated.spread(),
                conflated.adjustment(),
                derivedValue
        );
        for (final DerivedMarketDataUpdateListener listener : derivedUpdateListeners) {
            listener.onUpdate(derivedMarketData);
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

    public void addDerivedMarketDataUpdateListener(final DerivedMarketDataUpdateListener listener) {
        derivedUpdateListeners.add(Objects.requireNonNull(listener, "listener"));
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
        final int requiredCapacity = (int) ((expectedDistinctInstruments * (long) LOAD_FACTOR_DENOMINATOR
                + LOAD_FACTOR_NUMERATOR - 1L) / LOAD_FACTOR_NUMERATOR);
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

    private static void logInfo(final String message) {
        LOGGER.info(message);
    }

}
