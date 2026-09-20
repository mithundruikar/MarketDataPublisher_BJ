package com.bj.marketdata.source;

import java.nio.file.Path;
import java.util.Objects;

public class FileSource implements MarketDataSource {
    private final Path inputPath;

    public FileSource(Path inputPath) {
        this.inputPath = Objects.requireNonNull(inputPath, "inputPath");
    }

    public Path inputPath() {
        return inputPath;
    }

    @Override
    public void start(MarketDataListener listener) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void stop() {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
