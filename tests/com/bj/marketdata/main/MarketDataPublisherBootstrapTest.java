package com.bj.marketdata.main;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class MarketDataPublisherBootstrapTest {
    private MarketDataPublisherBootstrapTest() {
    }

    public static void main(String[] args) throws IOException {
        Properties properties = new Properties();
        Path propertiesPath = Path.of("tests/resources/market-data-publisher-test.properties");
        try (InputStream inputStream = Files.newInputStream(propertiesPath)) {
            properties.load(inputStream);
        }

        if (!properties.containsKey("service.name")) {
            throw new AssertionError("Missing property: service.name");
        }
    }
}
