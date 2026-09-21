package com.bj.marketdata;

import com.bj.marketdata.consumer.ConsumerConnectionHandler;
import com.bj.marketdata.multiplexer.EventLoop;
import com.bj.marketdata.service.DerivedMarketDataService;
import com.bj.marketdata.source.UdpSource;
import com.bj.marketdata.source.file.MarketRawUpdateFileSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Objects;
import java.util.Properties;

/**
 * Skeleton entry point placeholder for MarketDataPublisher service.
 */
public final class MarketDataPublisherApplication {
    private static final InetSocketAddress DEFAULT_UDP_BIND = new InetSocketAddress("127.0.0.1", 9001);

    private MarketDataPublisherApplication() {
    }

    public static Wiring wire(Path propertiesPath) throws IOException {
        Objects.requireNonNull(propertiesPath, "propertiesPath");
        final Properties properties = new Properties();
        try (final InputStream inputStream = Files.newInputStream(propertiesPath)) {
            properties.load(inputStream);
        }
        return wire(properties);
    }

    public static Wiring wire(Properties properties) throws IOException {
        Objects.requireNonNull(properties, "properties");
        final String sourceName = readRequiredProperty(properties, "rawUpdate.source");

        final EventLoop eventLoop = new EventLoop();
        final DerivedMarketDataService derivedMarketDataService = new DerivedMarketDataService();
        final ConsumerConnectionHandler consumerConnectionHandler = new ConsumerConnectionHandler(eventLoop, properties);
        final MarketRawUpdateFileSource fileSource =
                new MarketRawUpdateFileSource(sourceName, Path.of(readRequiredProperty(properties, "rawUpdate.file")), eventLoop);
        final UdpSource udpSource =
                new UdpSource(sourceName, readRequiredProperty(properties, "rawUpdate.udp.connect"), DEFAULT_UDP_BIND, eventLoop);

        fileSource.addListener(derivedMarketDataService);
        derivedMarketDataService.addDerivedMarketDataUpdateListener(consumerConnectionHandler.derivedUpdateListener());
        return new Wiring(eventLoop, derivedMarketDataService, fileSource, udpSource, consumerConnectionHandler);
    }

    public record Wiring(
            EventLoop eventLoop,
            DerivedMarketDataService derivedMarketDataService,
            MarketRawUpdateFileSource fileSource,
            UdpSource udpSource,
            ConsumerConnectionHandler consumerConnectionHandler
    ) {
    }

    private static String readRequiredProperty(Properties properties, String key) {
        final String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required property: " + key);
        }
        return value.trim();
    }
}
