package com.bj.marketdata.main;

import com.bj.marketdata.MarketDataPublisherApplication;
import com.bj.marketdata.source.MarketDataRawUpdate;
import com.bj.marketdata.source.MarketDataListener;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Properties;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

        assertTrue(properties.containsKey(MarketDataPublisherApplication.RAW_UPDATE_SOURCE_PROPERTY), "Missing property: rawUpdate.source");
        assertTrue(properties.containsKey(MarketDataPublisherApplication.RAW_UPDATE_FILE_PROPERTY), "Missing property: rawUpdate.file");
        assertTrue(properties.containsKey(MarketDataPublisherApplication.CONSUMER_CONNECT_LOGON_PROPERTY), "Missing property: consumer.connect.logon");
        assertTrue(properties.containsKey(MarketDataPublisherApplication.CONSUMER_CONNECT_UPDATES_PROPERTY), "Missing property: consumer.connect.updates");

        final MarketDataPublisherApplication application = new MarketDataPublisherApplication();
        final MarketDataPublisherApplication.Wiring wiring = application.wire(properties);
        try {
            final MarketDataRawUpdate[] firstUpdate = new MarketDataRawUpdate[1];
            wiring.fileSource().addListener(new MarketDataListener() {
                @Override
                public void onUpdate(final MarketDataRawUpdate update) {
                    firstUpdate[0] = update;
                }
            });
            wiring.fileSource().setReady(true);
            wiring.fileSource().read();

            assertNotNull(firstUpdate[0], "expected first update from file source");
            assertEquals("file", firstUpdate[0].source(), "unexpected source propagated to raw update");
            assertEquals(1L, firstUpdate[0].sequence(), "expected source-level sequence to start at 1");
            assertNotNull(wiring.udpSource(), "udp source should be wired");
        } finally {
            closeWiring(wiring);
        }
    }

    @Test
    void shouldFailWhenConfiguredFileDoesNotExist() {
        final Properties properties = new Properties();
        properties.setProperty(MarketDataPublisherApplication.RAW_UPDATE_SOURCE_PROPERTY, "file");
        properties.setProperty(MarketDataPublisherApplication.RAW_UPDATE_FILE_PROPERTY, "tests/resources/does-not-exist.tsv");
        properties.setProperty(MarketDataPublisherApplication.RAW_UPDATE_UDP_CONNECT_PROPERTY, "udp://127.0.0.1:9001");
        properties.setProperty(MarketDataPublisherApplication.CONSUMER_CONNECT_LOGON_PROPERTY, "tcp://127.0.0.1:0");
        properties.setProperty(MarketDataPublisherApplication.CONSUMER_CONNECT_UPDATES_PROPERTY, "udp://127.0.0.1:0");

        final MarketDataPublisherApplication application = new MarketDataPublisherApplication();
        assertThrows(IllegalArgumentException.class, () -> application.wire(properties));
    }

    @Test
    void shouldWireWhenRawUdpSourcePropertyIsMissing() throws Exception {
        final Properties properties = new Properties();
        final Path propertiesPath = Path.of(MarketDataPublisherBootstrapTest.class.getClassLoader()
                .getResource("market-data-publisher-test.properties")
                .toURI());
        try (final InputStream inputStream = Files.newInputStream(propertiesPath)) {
            properties.load(inputStream);
        }
        properties.remove(MarketDataPublisherApplication.RAW_UPDATE_UDP_CONNECT_PROPERTY);

        final MarketDataPublisherApplication application = new MarketDataPublisherApplication();
        final MarketDataPublisherApplication.Wiring wiring = application.wire(properties);
        try {
            assertNull(wiring.udpSource(), "udp source should not be created when rawUpdate.udp.connect is absent");
        } finally {
            closeWiring(wiring);
        }
    }

    @Test
    void shouldStartFileReadImmediatelyWhenConsumerEndpointsAreMissing() throws Exception {
        final Properties properties = new Properties();
        final Path propertiesPath = Path.of(MarketDataPublisherBootstrapTest.class.getClassLoader()
                .getResource("market-data-publisher-test.properties")
                .toURI());
        try (final InputStream inputStream = Files.newInputStream(propertiesPath)) {
            properties.load(inputStream);
        }
        properties.remove(MarketDataPublisherApplication.CONSUMER_CONNECT_LOGON_PROPERTY);
        properties.remove(MarketDataPublisherApplication.CONSUMER_CONNECT_UPDATES_PROPERTY);

        final MarketDataPublisherApplication application = new MarketDataPublisherApplication();
        final MarketDataPublisherApplication.Wiring wiring = application.wire(properties);
        try {
            final MarketDataRawUpdate[] firstUpdate = new MarketDataRawUpdate[1];
            wiring.fileSource().addListener(new MarketDataListener() {
                @Override
                public void onUpdate(final MarketDataRawUpdate update) {
                    firstUpdate[0] = update;
                }
            });
            wiring.fileSource().read();
            assertNotNull(firstUpdate[0], "expected immediate file read when consumer endpoints are absent");
            assertNull(wiring.consumerConnectionHandler(), "consumer connection handler should be absent");
        } finally {
            closeWiring(wiring);
        }
    }

    @Test
    void shouldFailWhenOnlyOneConsumerEndpointIsConfigured() {
        final Properties properties = new Properties();
        properties.setProperty(MarketDataPublisherApplication.RAW_UPDATE_SOURCE_PROPERTY, "file");
        properties.setProperty(MarketDataPublisherApplication.RAW_UPDATE_FILE_PROPERTY, "tests/resources/market-raw-updates-sample.csv");
        properties.setProperty(MarketDataPublisherApplication.CONSUMER_CONNECT_LOGON_PROPERTY, "tcp://127.0.0.1:0");

        final MarketDataPublisherApplication application = new MarketDataPublisherApplication();
        assertThrows(IllegalArgumentException.class, () -> application.wire(properties));
    }

    private static void closeWiring(final MarketDataPublisherApplication.Wiring wiring) throws Exception {
        if (wiring.consumerConnectionHandler() != null) {
            wiring.consumerConnectionHandler().close();
        }
        wiring.eventLoop().close();
    }
}
