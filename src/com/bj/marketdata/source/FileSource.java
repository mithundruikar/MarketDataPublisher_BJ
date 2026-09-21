package com.bj.marketdata.source;

import com.bj.marketdata.multiplexer.EventLoop;
import com.bj.marketdata.entity.MarketDataRawUpdate;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class FileSource implements InternalSource {
    private final Path inputPath;
    private final EventLoop eventLoop;
    private MarketDataListener listener;
    private BufferedReader reader;
    private boolean headerConsumed;
    private boolean completed;

    public FileSource(Path inputPath, EventLoop eventLoop) {
        this.inputPath = Objects.requireNonNull(inputPath, "inputPath");
        this.eventLoop = Objects.requireNonNull(eventLoop, "eventLoop");
        this.eventLoop.registerInternalSource(this);
    }

    public Path inputPath() {
        return inputPath;
    }

    @Override
    public void addListener(MarketDataListener listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    @Override
    public void read() throws IOException {
        if (completed) {
            return;
        }
        if (reader == null) {
            reader = Files.newBufferedReader(inputPath, StandardCharsets.UTF_8);
        }
        if (!headerConsumed) {
            reader.readLine();
            headerConsumed = true;
        }

        String line = reader.readLine();
        if (line == null) {
            completed = true;
            closeReader();
            eventLoop.unregisterInternalSource(this);
            return;
        }
        if (listener == null) {
            return;
        }
        if (line.isBlank()) {
            listener.onRejected(line, "blank_line");
            return;
        }

        ParsedFields parsed = parseLine(line);
        if (parsed == null) {
            listener.onRejected(line, "invalid_format");
            return;
        }

        try {
            long timestampMs = Long.parseLong(parsed.timestampRaw());
            double value = Double.parseDouble(parsed.valueRaw());
            listener.onUpdate(new MarketDataRawUpdate(timestampMs, parsed.instrument(), parsed.inputType(), value));
        } catch (NumberFormatException ex) {
            listener.onRejected(line, "invalid_numeric");
        }
    }

    private ParsedFields parseLine(String line) {
        char delimiter = detectDelimiter(line);
        if (delimiter == 0) {
            return null;
        }

        int c1 = line.indexOf(delimiter);
        if (c1 < 0) return null;
        int c2 = line.indexOf(delimiter, c1 + 1);
        if (c2 < 0) return null;
        int c3 = line.indexOf(delimiter, c2 + 1);
        if (c3 < 0 || line.indexOf(delimiter, c3 + 1) >= 0) return null;

        String timestampRaw = line.substring(0, c1).trim();
        String instrument = line.substring(c1 + 1, c2).trim();
        String inputType = line.substring(c2 + 1, c3).trim();
        String valueRaw = line.substring(c3 + 1).trim();
        if (timestampRaw.isEmpty() || instrument.isEmpty() || inputType.isEmpty() || valueRaw.isEmpty()) {
            return null;
        }
        return new ParsedFields(timestampRaw, instrument, inputType, valueRaw);
    }

    private char detectDelimiter(String line) {
        int comma = line.indexOf(',');
        int tab = line.indexOf('\t');
        if (comma < 0 && tab < 0) {
            return 0;
        }
        if (comma < 0) {
            return '\t';
        }
        if (tab < 0) {
            return ',';
        }
        return comma < tab ? ',' : '\t';
    }

    private void closeReader() throws IOException {
        if (reader != null) {
            reader.close();
            reader = null;
        }
    }

    private record ParsedFields(String timestampRaw, String instrument, String inputType, String valueRaw) {
    }
}
