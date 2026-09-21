package com.bj.marketdata.source;

import com.bj.marketdata.multiplexer.EventLoop;
import com.bj.marketdata.source.file.MarketRawUpdateFileSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileSourceTest {
    @Test
    void shouldReadFirstUpdateFromFileSource() throws Exception {
        final EventLoop eventLoop = new EventLoop();
        final Path samplePath = Path.of(FileSourceTest.class.getClassLoader()
                .getResource("market-raw-updates-sample.csv")
                .toURI());
        final MarketRawUpdateFileSource fileSource = new MarketRawUpdateFileSource("file", samplePath, eventLoop);
        final MarketDataRawUpdate[] firstUpdate = new MarketDataRawUpdate[1];

        fileSource.addListener(new MarketDataListener() {
            @Override
            public void onUpdate(final MarketDataRawUpdate update) {
                firstUpdate[0] = update;
            }
        });

        try {
            fileSource.setReady(true);
            fileSource.read();
            assertNotNull(firstUpdate[0], "expected first update callback");
            assertEquals("file", firstUpdate[0].source(), "unexpected source");
            assertEquals("ALPHA", firstUpdate[0].instrument(), "unexpected instrument");
        } finally {
            eventLoop.close();
        }
    }

    @Test
    void shouldNotReadWhenSourceIsNotReady() throws Exception {
        final EventLoop eventLoop = new EventLoop();
        final Path samplePath = Path.of(FileSourceTest.class.getClassLoader()
                .getResource("market-raw-updates-sample.csv")
                .toURI());
        final MarketRawUpdateFileSource fileSource = new MarketRawUpdateFileSource("file", samplePath, eventLoop);
        final MarketDataRawUpdate[] firstUpdate = new MarketDataRawUpdate[1];

        fileSource.addListener(new MarketDataListener() {
            @Override
            public void onUpdate(final MarketDataRawUpdate update) {
                firstUpdate[0] = update;
            }
        });

        try {
            fileSource.read();
            assertNull(firstUpdate[0], "source should stay paused while not ready");
        } finally {
            eventLoop.close();
        }
    }

    @Test
    void shouldFailWhenSourceFileMissing() throws Exception {
        final EventLoop eventLoop = new EventLoop();
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> new MarketRawUpdateFileSource("file", Path.of("tests/resources/missing-file.tsv"), eventLoop));
        } finally {
            eventLoop.close();
        }
    }
}
