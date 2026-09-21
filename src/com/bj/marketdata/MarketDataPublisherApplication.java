package com.bj.marketdata;

import com.bj.marketdata.multiplexer.EventLoop;
import com.bj.marketdata.service.DerivedMarketDataService;
import com.bj.marketdata.source.FileSource;
import com.bj.marketdata.source.UdpSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;

/**
 * Skeleton entry point placeholder for MarketDataPublisher service.
 */
public final class MarketDataPublisherApplication {
    private MarketDataPublisherApplication() {
    }

    public static Wiring wire(Path inputFilePath) throws IOException {
        EventLoop eventLoop = new EventLoop();
        DerivedMarketDataService derivedMarketDataService = new DerivedMarketDataService();
        FileSource fileSource = new FileSource(inputFilePath, eventLoop);
        UdpSource udpSource = new UdpSource(new InetSocketAddress("127.0.0.1", 9001), eventLoop);

        fileSource.addListener(derivedMarketDataService);
        return new Wiring(eventLoop, derivedMarketDataService, fileSource, udpSource);
    }

    public record Wiring(
            EventLoop eventLoop,
            DerivedMarketDataService derivedMarketDataService,
            FileSource fileSource,
            UdpSource udpSource
    ) {
    }
}
