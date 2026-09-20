package com.bj.marketdata.consumer;

import com.bj.marketdata.service.DerivedMarketData;

public interface DownstreamPublisher {
    void register(Subscription subscription);

    void unregister(String clientId);

    void publish(DerivedMarketData update);
}
