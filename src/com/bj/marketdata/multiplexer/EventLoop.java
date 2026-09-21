package com.bj.marketdata.multiplexer;

import com.bj.marketdata.source.InternalSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public final class EventLoop implements Runnable, AutoCloseable {
    private static final long SELECT_TIMEOUT_MILLIS = 25L;

    private final Selector selector;
    private final CopyOnWriteArrayList<InternalSource> internalSources = new CopyOnWriteArrayList<>();
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    public EventLoop() throws IOException {
        this.selector = Selector.open();
    }

    public void register(SelectableChannel channel, int interestOps, IOHandler handler) throws IOException {
        channel.configureBlocking(false);
        channel.register(selector, interestOps, handler);
        selector.wakeup();
    }

    public void registerInternalSource(InternalSource source) {
        internalSources.addIfAbsent(source);
    }

    public void unregisterInternalSource(InternalSource source) {
        internalSources.remove(source);
    }

    public void requestStop() {
        stopRequested.set(true);
        selector.wakeup();
    }

    @Override
    public void run() {
        while (!stopRequested.get()) {
            for (InternalSource source : internalSources) {
                try {
                    source.read();
                } catch (IOException e) {
                    throw new UncheckedIOException("Internal source read failed", e);
                }
            }

            try {
                selector.select(SELECT_TIMEOUT_MILLIS);
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectedKeys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();
                    if (!key.isValid()) {
                        continue;
                    }
                    Object attachment = key.attachment();
                    if (!(attachment instanceof IOHandler handler)) {
                        continue;
                    }

                    if (key.isAcceptable()) {
                        handler.onAccept(key);
                    }
                    if (key.isConnectable()) {
                        handler.onConnect(key);
                    }
                    if (key.isReadable()) {
                        handler.onRead(key);
                    }
                    if (key.isWritable()) {
                        handler.onWrite(key);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Event loop selector failure", e);
            }
        }
    }

    @Override
    public void close() throws IOException {
        requestStop();
        selector.close();
    }
}
