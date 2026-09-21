package com.bj.marketdata.main;

import com.bj.marketdata.MarketDataPublisherApplication;
import com.bj.marketdata.entity.MarketDataRawUpdate;
import com.bj.marketdata.source.MarketDataListener;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class MarketDataPublisherBootstrapTest {
    private MarketDataPublisherBootstrapTest() {
    }

    public static void main(String[] args) throws IOException {
        shouldWireFromProperties();
        shouldFailWhenConfiguredFileDoesNotExist();
    }

    private static void shouldWireFromProperties() throws IOException {
        Properties properties = new Properties();
        Path propertiesPath = Path.of("tests/resources/market-data-publisher-test.properties");
        try (InputStream inputStream = Files.newInputStream(propertiesPath)) {
            properties.load(inputStream);
        }

        if (!properties.containsKey("rawUpdate.source")) {
            throw new AssertionError("Missing property: rawUpdate.source");
        }
        if (!properties.containsKey("rawUpdate.file")) {
            throw new AssertionError("Missing property: rawUpdate.file");
        }
        if (!properties.containsKey("rawUpdate.udp.connect")) {
            throw new AssertionError("Missing property: rawUpdate.udp.connect");
        }

        MarketDataPublisherApplication.Wiring wiring = MarketDataPublisherApplication.wire(properties);
        try {
            MarketDataRawUpdate[] firstUpdate = new MarketDataRawUpdate[1];
            wiring.fileSource().addListener(new MarketDataListener() {
                @Override
                public void onUpdate(MarketDataRawUpdate update) {
                    firstUpdate[0] = update;
                }
            });
            wiring.fileSource().read();

            assert firstUpdate[0] != null : "expected first update from file source";
            assert "file".equals(firstUpdate[0].source()) : "unexpected source propagated to raw update";
            assert firstUpdate[0].sequence() == 1L : "expected source-level sequence to start at 1";
            assert wiring.udpSource() != null : "udp source should be wired";
        } finally {
            wiring.eventLoop().close();
        }
    }

    private static void shouldFailWhenConfiguredFileDoesNotExist() throws IOException {
        Properties properties = new Properties();
        properties.setProperty("rawUpdate.source", "file");
        properties.setProperty("rawUpdate.file", "tests/resources/does-not-exist.tsv");
        properties.setProperty("rawUpdate.udp.connect", "udp://127.0.0.1:9001");

        boolean thrown = false;
        try {
            MarketDataPublisherApplication.wire(properties);
        } catch (IllegalArgumentException expected) {
            thrown = true;
        }
        if (!thrown) {
            throw new AssertionError("Expected IllegalArgumentException for missing raw update file");
        }
    }
}
