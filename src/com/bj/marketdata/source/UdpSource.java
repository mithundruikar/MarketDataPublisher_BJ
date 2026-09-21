package com.bj.marketdata.source;

import com.bj.marketdata.multiplexer.EventLoop;
import com.bj.marketdata.multiplexer.IOHandler;

import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.util.Objects;

public class UdpSource implements Source, IOHandler {
    private final InetSocketAddress bindAddress;
    private final EventLoop eventLoop;
    private MarketDataListener listener;

    public UdpSource(InetSocketAddress bindAddress, EventLoop eventLoop) {
        this.bindAddress = Objects.requireNonNull(bindAddress, "bindAddress");
        this.eventLoop = Objects.requireNonNull(eventLoop, "eventLoop");
    }

    public InetSocketAddress bindAddress() {
        return bindAddress;
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

    public EventLoop eventLoop() {
        return eventLoop;
    }

    public MarketDataListener listener() {
        return listener;
    }
}
