package com.bj.marketdata.multiplexer;

import java.io.IOException;
import java.nio.channels.SelectionKey;

public interface IOHandler {
    default void onRead(SelectionKey key) throws IOException {
    }

    default void onWrite(SelectionKey key) throws IOException {
    }

    default void onAccept(SelectionKey key) throws IOException {
    }

    default void onConnect(SelectionKey key) throws IOException {
    }
}
