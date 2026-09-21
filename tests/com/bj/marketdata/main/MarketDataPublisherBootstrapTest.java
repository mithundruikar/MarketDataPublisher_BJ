package com.bj.marketdata.main;

import com.bj.marketdata.MarketDataPublisherApplication;
import com.bj.marketdata.entity.MarketDataRawUpdate;
import com.bj.marketdata.source.MarketDataListener;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketDataPublisherBootstrapTest {
    @Test
    void shouldWireFromProperties() throws Exception {
        final Properties properties = new Properties();
        final Path propertiesPath = Path.of(MarketDataPublisherBootstrapTest.class.getClassLoader()
                .getResource("market-data-publisher-test.properties")
                .toURI());
        try (final InputStream inputStream = Files.newInputStream(propertiesPath)) {
            properties.load(inputStream);
        }

        assertTrue(properties.containsKey("rawUpdate.source"), "Missing property: rawUpdate.source");
        assertTrue(properties.containsKey("rawUpdate.file"), "Missing property: rawUpdate.file");
        assertTrue(properties.containsKey("rawUpdate.udp.connect"), "Missing property: rawUpdate.udp.connect");
        assertTrue(properties.containsKey("consumer.connect.logon"), "Missing property: consumer.connect.logon");
        assertTrue(properties.containsKey("consumer.connect.updates"), "Missing property: consumer.connect.updates");

        final MarketDataPublisherApplication.Wiring wiring = MarketDataPublisherApplication.wire(properties);
        try {
            final MarketDataRawUpdate[] firstUpdate = new MarketDataRawUpdate[1];
            wiring.fileSource().addListener(new MarketDataListener() {
                @Override
                public void onUpdate(final MarketDataRawUpdate update) {
                    firstUpdate[0] = update;
                }
            });
            wiring.fileSource().read();

            assertNotNull(firstUpdate[0], "expected first update from file source");
            assertEquals("file", firstUpdate[0].source(), "unexpected source propagated to raw update");
            assertEquals(1L, firstUpdate[0].sequence(), "expected source-level sequence to start at 1");
            assertNotNull(wiring.udpSource(), "udp source should be wired");
        } finally {
            wiring.consumerHandler().close();
            wiring.eventLoop().close();
        }
    }

    @Test
    void shouldFailWhenConfiguredFileDoesNotExist() {
        final Properties properties = new Properties();
        properties.setProperty("rawUpdate.source", "file");
        properties.setProperty("rawUpdate.file", "tests/resources/does-not-exist.tsv");
        properties.setProperty("rawUpdate.udp.connect", "udp://127.0.0.1:9001");
        properties.setProperty("consumer.connect.logon", "tcp://127.0.0.1:0");
        properties.setProperty("consumer.connect.updates", "udp://127.0.0.1:0");

        assertThrows(IllegalArgumentException.class, () -> MarketDataPublisherApplication.wire(properties));
    }
}
