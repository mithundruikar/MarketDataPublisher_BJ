package com.bj.marketdata;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketDataPublisherApplicationCliTest {
    @Test
    void shouldBuildPropertiesFromNamedArguments() {
        final MarketDataPublisherApplication application = new MarketDataPublisherApplication();
        final String[] args = {
                "--raw_updates", "tests/resources/market-raw-updates-sample.csv",
                "--consumer.logon", "tcp://127.0.0.1:1111",
                "--consumer.update", "udp://127.0.0.1:2222"
        };

        final Properties properties = application.propertiesFromArgs(args);

        assertEquals(MarketDataPublisherApplication.DEFAULT_RAW_UPDATE_SOURCE,
                properties.getProperty(MarketDataPublisherApplication.RAW_UPDATE_SOURCE_PROPERTY));
        assertEquals("tests/resources/market-raw-updates-sample.csv",
                properties.getProperty(MarketDataPublisherApplication.RAW_UPDATE_FILE_PROPERTY));
        assertEquals("tcp://127.0.0.1:1111",
                properties.getProperty(MarketDataPublisherApplication.CONSUMER_CONNECT_LOGON_PROPERTY));
        assertEquals("udp://127.0.0.1:2222",
                properties.getProperty(MarketDataPublisherApplication.CONSUMER_CONNECT_UPDATES_PROPERTY));
    }

    @Test
    void shouldLeaveConsumerEndpointsUnsetWhenOptionalArgumentsAreMissing() {
        final MarketDataPublisherApplication application = new MarketDataPublisherApplication();
        final String[] args = {"--raw_updates", "tests/resources/market-raw-updates-sample.csv"};

        final Properties properties = application.propertiesFromArgs(args);

        assertNull(properties.getProperty(MarketDataPublisherApplication.CONSUMER_CONNECT_LOGON_PROPERTY));
        assertNull(properties.getProperty(MarketDataPublisherApplication.CONSUMER_CONNECT_UPDATES_PROPERTY));
    }

    @Test
    void shouldFailWhenRawUpdatesArgumentIsMissing() {
        final MarketDataPublisherApplication application = new MarketDataPublisherApplication();
        final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> application.propertiesFromArgs(new String[] {"--consumer.logon", "tcp://127.0.0.1:0"})
        );
        assertTrue(exception.getMessage().contains("--raw_updates"));
    }
}
