package com.bj.marketdata.source;

public interface MarketDataSource {
    void start(MarketDataListener listener);

    void stop();
}
