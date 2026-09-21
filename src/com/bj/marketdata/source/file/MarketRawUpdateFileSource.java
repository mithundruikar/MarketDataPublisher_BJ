package com.bj.marketdata.source.file;

import com.bj.marketdata.multiplexer.EventLoop;
import com.bj.marketdata.source.InternalSource;
import com.bj.marketdata.source.MarketDataListener;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class MarketRawUpdateFileSource implements InternalSource {
    private final String sourceName;
    private final Path inputPath;
    private final EventLoop eventLoop;
    private final MarketRawFileReader fileReader;
    private MarketDataListener listener;

    public MarketRawUpdateFileSource(String sourceName, Path inputPath, EventLoop eventLoop) {
        this.sourceName = Objects.requireNonNull(sourceName, "sourceName");
        this.inputPath = Objects.requireNonNull(inputPath, "inputPath");
        this.eventLoop = Objects.requireNonNull(eventLoop, "eventLoop");
        if (!Files.isRegularFile(this.inputPath)) {
            throw new IllegalArgumentException("raw update file not found: " + this.inputPath);
        }
        this.fileReader = new MarketRawFileReader(this.sourceName, this.inputPath);
        this.eventLoop.registerInternalSource(this);
    }

    @Override
    public void addListener(MarketDataListener listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    @Override
    public void read() throws IOException {
        final MarketRawFileReader.ReadResult result = fileReader.readNext();
        if (result.completed()) {
            eventLoop.unregisterInternalSource(this);
            return;
        }
        if (listener == null) {
            return;
        }
        if (result.update() != null) {
            listener.onUpdate(result.update());
            return;
        }
        listener.onRejected(result.rawRecord(), result.rejectionReason());
    }
}
