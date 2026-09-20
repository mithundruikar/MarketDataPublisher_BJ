package com.bj.marketdata.multiplexer;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

/**
 * NIO selector/event-loop contract.
 */
public interface NioEventLoop {
    void start();

    void stop();

    void register(SelectableChannel channel, int interestOps, Object attachment);

    void onReadable(SelectionKey key);

    void onWritable(SelectionKey key);
}
