package com.bj.marketdata.source;

import com.bj.marketdata.multiplexer.EventLoop;
import com.bj.marketdata.multiplexer.IOHandler;

import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.util.Objects;

public final class UdpSource implements Source, IOHandler {
    private final String sourceName;
    private final String udpConnect;
    private final InetSocketAddress bindAddress;
    private final EventLoop eventLoop;
    private MarketDataListener listener;

    public UdpSource(String sourceName, String udpConnect, InetSocketAddress bindAddress, EventLoop eventLoop) {
        this.sourceName = Objects.requireNonNull(sourceName, "sourceName");
        this.udpConnect = Objects.requireNonNull(udpConnect, "udpConnect");
        this.bindAddress = Objects.requireNonNull(bindAddress, "bindAddress");
        this.eventLoop = Objects.requireNonNull(eventLoop, "eventLoop");
    }

    @Override
    public void addListener(MarketDataListener listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    @Override
    public void onRead(SelectionKey key) {
        // no-op skeleton
    }

    @Override
    public void onWrite(SelectionKey key) {
        // no-op skeleton
    }

    @Override
    public void onAccept(SelectionKey key) {
        // no-op skeleton
    }

    @Override
    public void onConnect(SelectionKey key) {
        // no-op skeleton
    }
}
