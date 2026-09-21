package com.bj.marketdata.source.file;

import com.bj.marketdata.entity.InstrumentUpdateType;
import com.bj.marketdata.entity.MarketDataRawUpdate;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class MarketRawFileReaderTest {
    @Test
    void shouldParseSampleFileAndEmitSequentialUpdates() throws IOException, URISyntaxException {
        final MarketRawFileReader reader = new MarketRawFileReader("file", sampleFilePath());
        final List<MarketDataRawUpdate> updates = readAllUpdates(reader);

        assertEquals(29, updates.size(), "expected 29 parsed updates");

        final MarketDataRawUpdate first = updates.get(0);
        assertEquals(1_733_011_200_000L, first.updateTimeMillis(), "unexpected first update timestamp");
        assertEquals("file", first.source(), "unexpected source name");
        assertEquals("ALPHA", first.instrument(), "unexpected first instrument");
        assertEquals(InstrumentUpdateType.BASE_RATE, first.inputType(), "unexpected first input type");
        assertEquals(4.6394, first.value(), 1e-9, "unexpected first value");

        final MarketDataRawUpdate last = updates.get(updates.size() - 1);
        assertEquals(1_733_011_200_382L, last.updateTimeMillis(), "unexpected last update timestamp");
        assertEquals("DELTA", last.instrument(), "unexpected last instrument");
        assertEquals(InstrumentUpdateType.BASE_RATE, last.inputType(), "unexpected last input type");
        assertEquals(4.6554, last.value(), 1e-9, "unexpected last value");

        for (int i = 1; i < updates.size(); i++) {
            final long prev = updates.get(i - 1).sequence();
            final long current = updates.get(i).sequence();
            assertEquals(prev + 1, current, "sequence should be contiguous");
        }
    }

    @Test
    void shouldKeepSequenceScopedToSourceReader() throws IOException, URISyntaxException {
        final Path sample = sampleFilePath();

        final MarketRawFileReader reader1 = new MarketRawFileReader("file-A", sample);
        final List<MarketDataRawUpdate> run1 = readAllUpdates(reader1);
        final long lastSeqRun1 = run1.get(run1.size() - 1).sequence();
        assertEquals(run1.size(), lastSeqRun1, "first source reader sequence should be contiguous from 1");

        final MarketRawFileReader reader2 = new MarketRawFileReader("file-B", sample);
        final MarketDataRawUpdate firstRun2 = readFirstUpdate(reader2);

        assertNotNull(firstRun2, "expected first update in second run");
        assertEquals(1L, firstRun2.sequence(), "sequence should restart for a different source reader");
        assertEquals("file-B", firstRun2.source(), "reader should stamp its source name");
    }

    private static List<MarketDataRawUpdate> readAllUpdates(final MarketRawFileReader reader) throws IOException {
        final List<MarketDataRawUpdate> updates = new ArrayList<>();
        while (true) {
            final MarketRawFileReader.ReadResult result = reader.readNext();
            if (result.completed()) {
                break;
            }
            assertNull(result.rejectionReason(), "unexpected rejection: " + result.rejectionReason());
            assertNotNull(result.update(), "expected parsed update");
            updates.add(result.update());
        }
        return updates;
    }

    private static MarketDataRawUpdate readFirstUpdate(final MarketRawFileReader reader) throws IOException {
        while (true) {
            final MarketRawFileReader.ReadResult result = reader.readNext();
            if (result.completed()) {
                return null;
            }
            if (result.update() != null) {
                return result.update();
            }
        }
    }

    private static Path sampleFilePath() throws URISyntaxException {
        return Path.of(Objects.requireNonNull(
                MarketRawFileReaderTest.class.getClassLoader().getResource("market-raw-updates-sample.tsv"))
                .toURI());
    }
}
