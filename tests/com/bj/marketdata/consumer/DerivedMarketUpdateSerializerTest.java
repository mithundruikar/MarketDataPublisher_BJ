package com.bj.marketdata.consumer;

import com.bj.marketdata.service.DerivedMarketData;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DerivedMarketUpdateSerializerTest {
    @Test
    void shouldSerializeToCsvTimestampInstrumentDerivedValue() {
        final DerivedMarketUpdateSerializer serializer = new DerivedMarketUpdateSerializer();
        final DerivedMarketData update = new DerivedMarketData(1_733_011_200_000L, "ALPHA", 4.6394, 0.0588, -0.0450, 4.6532);

        final byte[] payload = serializer.serialize(update);

        assertEquals("1733011200000,ALPHA,4.6532\n", new String(payload, StandardCharsets.UTF_8));
    }
}
