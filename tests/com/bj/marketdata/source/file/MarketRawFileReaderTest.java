package com.bj.marketdata.source.file;

import com.bj.marketdata.entity.MarketDataRawUpdate;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class MarketRawFileReaderTest {
    private MarketRawFileReaderTest() {
    }

    public static void main(String[] args) throws IOException {
        shouldParseSampleFileAndEmitSequentialUpdates();
        shouldKeepSequenceScopedToSourceReader();
    }

    private static void shouldParseSampleFileAndEmitSequentialUpdates() throws IOException {
        MarketRawFileReader reader = new MarketRawFileReader("file", Path.of("tests/resources/market-raw-updates-sample.tsv"));
        List<MarketDataRawUpdate> updates = readAllUpdates(reader);

        assert updates.size() == 29 : "expected 29 parsed updates";

        MarketDataRawUpdate first = updates.get(0);
        assert first.updateTimeMillis() == 1_733_011_200_000L : "unexpected first update timestamp";
        assert "file".equals(first.source()) : "unexpected source name";
        assert "ALPHA".equals(first.instrument()) : "unexpected first instrument";
        assert "base_rate".equals(first.inputType()) : "unexpected first input type";
        assert closeTo(first.value(), 4.6394) : "unexpected first value";

        MarketDataRawUpdate last = updates.get(updates.size() - 1);
        assert last.updateTimeMillis() == 1_733_011_200_382L : "unexpected last update timestamp";
        assert "DELTA".equals(last.instrument()) : "unexpected last instrument";
        assert "base_rate".equals(last.inputType()) : "unexpected last input type";
        assert closeTo(last.value(), 4.6554) : "unexpected last value";

        for (int i = 1; i < updates.size(); i++) {
            long prev = updates.get(i - 1).sequence();
            long current = updates.get(i).sequence();
            assert current == prev + 1 : "sequence should be contiguous";
        }
    }

    private static void shouldKeepSequenceScopedToSourceReader() throws IOException {
        Path sample = Path.of("tests/resources/market-raw-updates-sample.tsv");

        MarketRawFileReader reader1 = new MarketRawFileReader("file-A", sample);
        List<MarketDataRawUpdate> run1 = readAllUpdates(reader1);
        long lastSeqRun1 = run1.get(run1.size() - 1).sequence();
        assert lastSeqRun1 == run1.size() : "first source reader sequence should be contiguous from 1";

        MarketRawFileReader reader2 = new MarketRawFileReader("file-B", sample);
        MarketDataRawUpdate firstRun2 = readFirstUpdate(reader2);

        assert firstRun2 != null : "expected first update in second run";
        assert firstRun2.sequence() == 1 : "sequence should restart for a different source reader";
        assert "file-B".equals(firstRun2.source()) : "reader should stamp its source name";
    }

    private static List<MarketDataRawUpdate> readAllUpdates(MarketRawFileReader reader) throws IOException {
        List<MarketDataRawUpdate> updates = new ArrayList<>();
        while (true) {
            MarketRawFileReader.ReadResult result = reader.readNext();
            if (result.completed()) {
                break;
            }
            assert result.rejectionReason() == null : "unexpected rejection: " + result.rejectionReason();
            assert result.update() != null : "expected parsed update";
            updates.add(result.update());
        }
        return updates;
    }

    private static MarketDataRawUpdate readFirstUpdate(MarketRawFileReader reader) throws IOException {
        while (true) {
            MarketRawFileReader.ReadResult result = reader.readNext();
            if (result.completed()) {
                return null;
            }
            if (result.update() != null) {
                return result.update();
            }
        }
    }

    private static boolean closeTo(double actual, double expected) {
        return Math.abs(actual - expected) < 1e-9;
    }
}
