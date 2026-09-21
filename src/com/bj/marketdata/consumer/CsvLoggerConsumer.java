package com.bj.marketdata.consumer;

import com.bj.marketdata.service.DerivedMarketData;
import com.bj.marketdata.service.DerivedMarketDataUpdateListener;

import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

public final class CsvLoggerConsumer implements DerivedMarketDataUpdateListener {
    private static final Logger LOGGER = Logger.getLogger(CsvLoggerConsumer.class.getName());

    private final DerivedMarketUpdateSerializer serializer = new DerivedMarketUpdateSerializer();

    @Override
    public void onUpdate(final DerivedMarketData update) {
        final String csvLine = new String(serializer.serialize(update), StandardCharsets.UTF_8).trim();
        LOGGER.info(csvLine);
    }
}
