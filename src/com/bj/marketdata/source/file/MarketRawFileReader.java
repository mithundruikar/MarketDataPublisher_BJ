package com.bj.marketdata.source.file;

import com.bj.marketdata.entity.MarketDataRawUpdate;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

final class MarketRawFileReader {
    private final String sourceName;
    private final Path inputPath;
    private long sourceSequence = 0L;
    private BufferedReader reader;
    private boolean headerConsumed;
    private boolean completed;

    MarketRawFileReader(String sourceName, Path inputPath) {
        this.sourceName = Objects.requireNonNull(sourceName, "sourceName");
        this.inputPath = Objects.requireNonNull(inputPath, "inputPath");
    }

    ReadResult readNext() throws IOException {
        if (completed) {
            return ReadResult.done();
        }
        if (reader == null) {
            reader = Files.newBufferedReader(inputPath, StandardCharsets.UTF_8);
        }
        if (!headerConsumed) {
            final String header = reader.readLine();
            headerConsumed = true;
            if (header == null) {
                completed = true;
                closeReader();
                return ReadResult.done();
            }
        }

        final String line = reader.readLine();
        if (line == null) {
            completed = true;
            closeReader();
            return ReadResult.done();
        }
        if (line.isBlank()) {
            return ReadResult.rejected(line, "blank_line");
        }

        final ParsedFields parsed = parseLine(line);
        if (parsed == null) {
            return ReadResult.rejected(line, "invalid_format");
        }

        try {
            final long timestampMs = Long.parseLong(parsed.timestampRaw());
            final double value = Double.parseDouble(parsed.valueRaw());
            final long sequence = ++sourceSequence;
            return ReadResult.update(new MarketDataRawUpdate(
                    sequence,
                    timestampMs,
                    sourceName,
                    parsed.instrument(),
                    parsed.inputType(),
                    value
            ));
        } catch (NumberFormatException ex) {
            return ReadResult.rejected(line, "invalid_numeric");
        }
    }

    private ParsedFields parseLine(String line) {
        final char delimiter = detectDelimiter(line);
        if (delimiter == 0) {
            return null;
        }

        final int c1 = line.indexOf(delimiter);
        if (c1 < 0) return null;
        final int c2 = line.indexOf(delimiter, c1 + 1);
        if (c2 < 0) return null;
        final int c3 = line.indexOf(delimiter, c2 + 1);
        if (c3 < 0 || line.indexOf(delimiter, c3 + 1) >= 0) return null;

        final String timestampRaw = line.substring(0, c1).trim();
        final String instrument = line.substring(c1 + 1, c2).trim();
        final String inputType = line.substring(c2 + 1, c3).trim();
        final String valueRaw = line.substring(c3 + 1).trim();
        if (timestampRaw.isEmpty() || instrument.isEmpty() || inputType.isEmpty() || valueRaw.isEmpty()) {
            return null;
        }
        return new ParsedFields(timestampRaw, instrument, inputType, valueRaw);
    }

    private char detectDelimiter(String line) {
        final int comma = line.indexOf(',');
        final int tab = line.indexOf('\t');
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

    record ReadResult(MarketDataRawUpdate update, String rawRecord, String rejectionReason, boolean completed) {
        static ReadResult update(MarketDataRawUpdate update) {
            return new ReadResult(update, null, null, false);
        }

        static ReadResult rejected(String rawRecord, String rejectionReason) {
            return new ReadResult(null, rawRecord, rejectionReason, false);
        }

        static ReadResult done() {
            return new ReadResult(null, null, null, true);
        }
    }

    private record ParsedFields(String timestampRaw, String instrument, String inputType, String valueRaw) {
    }
}
