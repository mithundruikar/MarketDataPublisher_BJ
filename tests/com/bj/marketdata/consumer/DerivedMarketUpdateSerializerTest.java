package com.bj.marketdata.consumer;

import com.bj.marketdata.service.DerivedMarketData;
import com.bj.marketdata.value.ScaledPrice;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DerivedMarketUpdateSerializerTest {
    @Test
    void shouldSerializeToCsvTimestampInstrumentDerivedValue() {
        final DerivedMarketUpdateSerializer serializer = new DerivedMarketUpdateSerializer();
        final DerivedMarketData update = new DerivedMarketData(
                1_733_011_200_000L,
                "ALPHA",
                scaled("4.6394"),
                scaled("0.0588"),
                scaled("-0.0450"),
                scaled("4.6532")
        );

        final byte[] payload = serializer.serialize(update);

        assertEquals("1733011200000,ALPHA,4.6532\n", new String(payload, StandardCharsets.UTF_8));
    }

    private static long scaled(final String value) {
        return ScaledPrice.parse(value);
    }
}
