package com.bj.marketdata.consumer;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Objects;

public final class Subscription {
    private final long subscriptionId;
    private final String clientId;
    private final List<String> filterInstruments;
    private InetSocketAddress currentInetSocketAddress;

    public Subscription(final long subscriptionId, final String clientId) {
        this.subscriptionId = subscriptionId;
        this.clientId = Objects.requireNonNull(clientId, "clientId");
        this.filterInstruments = List.of();
    }

    public long subscriptionId() {
        return subscriptionId;
    }

    public String clientId() {
        return clientId;
    }

    public List<String> filterInstruments() {
        return filterInstruments;
    }

    public InetSocketAddress currentInetSocketAddress() {
        return currentInetSocketAddress;
    }

    public void updateCurrentInetSocketAddress(final InetSocketAddress currentInetSocketAddress) {
        this.currentInetSocketAddress = currentInetSocketAddress;
    }
}
