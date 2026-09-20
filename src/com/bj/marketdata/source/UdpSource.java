package com.bj.marketdata.source;

import java.net.InetSocketAddress;
import java.util.Objects;

public class UdpSource implements MarketDataSource {
    private final InetSocketAddress bindAddress;

    public UdpSource(InetSocketAddress bindAddress) {
        this.bindAddress = Objects.requireNonNull(bindAddress, "bindAddress");
    }

    public InetSocketAddress bindAddress() {
        return bindAddress;
    }

    @Override
    public void start(MarketDataListener listener) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void stop() {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
