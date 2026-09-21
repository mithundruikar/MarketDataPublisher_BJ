package com.bj.marketdata.multiplexer;

import com.bj.marketdata.source.InternalSource;
import com.bj.marketdata.source.MarketDataListener;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EventLoopTest {
    @Test
    void shouldPollRegisteredInternalSources() throws Exception {
        final EventLoop eventLoop = new EventLoop();
        final AtomicInteger readCalls = new AtomicInteger(0);

        final InternalSource source = new InternalSource() {
            @Override
            public void addListener(final MarketDataListener listener) {
                // no-op test source
            }

            @Override
            public void read() {
                if (readCalls.incrementAndGet() >= 2) {
                    eventLoop.requestStop();
                }
            }
        };

        eventLoop.registerInternalSource(source);
        final Thread loopThread = new Thread(eventLoop, "event-loop-test-thread");
        loopThread.start();
        loopThread.join(2_000);

        eventLoop.close();
        assertTrue(readCalls.get() >= 1, "expected internal source to be polled");
    }
}
