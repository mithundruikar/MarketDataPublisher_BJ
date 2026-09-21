package com.bj.marketdata.source.file;

import com.bj.marketdata.multiplexer.EventLoop;
import com.bj.marketdata.source.InternalSource;
import com.bj.marketdata.source.MarketDataListener;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Logger;

public final class MarketRawUpdateFileSource implements InternalSource {
    private static final Logger LOGGER = Logger.getLogger(MarketRawUpdateFileSource.class.getName());
    private final String sourceName;
    private final Path inputPath;
    private final EventLoop eventLoop;
    private final MarketRawFileReader fileReader;
    // Read gating switch: when false, source stays paused and emits nothing until explicitly enabled.
    private boolean ready;
    private boolean readingStartedLogged;
    private boolean readingCompletedLogged;
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

    public void setReady(final boolean ready) {
        this.ready = ready;
    }

    @Override
    public void read() throws IOException {
        if (!ready) {
            return;
        }
        if (!readingStartedLogged) {
            logInfo("Starting raw update read from file: " + inputPath);
            readingStartedLogged = true;
        }
        final MarketRawFileReader.ReadResult result = fileReader.readNext();
        if (result.completed()) {
            eventLoop.unregisterInternalSource(this);
            if (!readingCompletedLogged) {
                logInfo("Completed raw update read from file: " + inputPath);
                readingCompletedLogged = true;
            }
            return;
        }
        if (listener == null) {
            return;
        }
        if (result.update() != null) {
            listener.onUpdate(result.update());
            return;
        }
        if ("invalid_numeric".equals(result.rejectionReason())) {
            logError("Rejected raw update line due to invalid numeric value: " + result.rawRecord());
        }
        listener.onRejected(result.rawRecord(), result.rejectionReason());
    }

    private static void logInfo(final String message) {
        LOGGER.info(message);
    }

    private static void logError(final String message) {
        LOGGER.severe(message);
    }
}
