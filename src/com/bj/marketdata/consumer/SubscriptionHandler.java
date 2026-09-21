package com.bj.marketdata.consumer;

import com.bj.marketdata.multiplexer.EventLoop;
import com.bj.marketdata.multiplexer.IOHandler;
import com.bj.marketdata.service.DerivedMarketData;
import com.bj.marketdata.service.DerivedMarketDataUpdateListener;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

final class SubscriptionHandler implements IOHandler, DerivedMarketDataUpdateListener {
    private static final int REQUEST_BUFFER_SIZE_BYTES = 512;
    private static final int MAX_PENDING_REALTIME_UPDATES = 4_096;

    // This is a fixed-size ring buffer by design: it never blocks and never grows.
    // If producers outpace UDP writes, we drop the oldest queued payload and keep the newest one.
    // That avoids backpressure and protects latency on the single-threaded event loop path.
    private final byte[][] pendingRealtimeUpdates = new byte[MAX_PENDING_REALTIME_UPDATES][];

    private final EventLoop eventLoop;
    private final DatagramChannel updatesDatagramChannel;
    private final DerivedMarketUpdateSerializer serializer = new DerivedMarketUpdateSerializer();
    private final Map<Long, Subscription> subscriptions = new HashMap<>();

    private final ByteBuffer udpReadBuffer = ByteBuffer.allocate(REQUEST_BUFFER_SIZE_BYTES);

    private boolean updatesWriteRegistered;
    private int pendingRealtimeHead;
    private int pendingRealtimeTail;
    private int pendingRealtimeSize;

    SubscriptionHandler(final EventLoop eventLoop, final DatagramChannel updatesDatagramChannel) {
        this.eventLoop = Objects.requireNonNull(eventLoop, "eventLoop");
        this.updatesDatagramChannel = Objects.requireNonNull(updatesDatagramChannel, "updatesDatagramChannel");
    }

    void registerSubscription(final String clientId, final long subscriptionId) {
        subscriptions.put(subscriptionId, new Subscription(subscriptionId, clientId));
    }

    int subscriptionCount() {
        return subscriptions.size();
    }

    int realtimeEndpointCount() {
        int count = 0;
        for (final Subscription subscription : subscriptions.values()) {
            if (subscription.currentInetSocketAddress() != null) {
                count++;
            }
        }
        return count;
    }

    void publishRealtimeUpdate(final String payload) {
        publishRealtimeUpdate(Objects.requireNonNull(payload, "payload").getBytes(StandardCharsets.UTF_8));
    }

    void publishRealtimeUpdate(final byte[] payload) {
        enqueueRealtimeUpdate(Objects.requireNonNull(payload, "payload"));
        try {
            registerUpdatesWrites();
        } catch (final IOException exception) {
            throw new IllegalStateException("Failed to register write interest for realtime updates", exception);
        }
    }

    @Override
    public void onUpdate(final DerivedMarketData update) {
        final byte[] payload = serializer.serialize(update);
        publishRealtimeUpdate(payload);
    }

    @Override
    public void onRead(final SelectionKey key) throws IOException {
        if (!(key.channel() instanceof DatagramChannel datagramChannel)) {
            return;
        }
        registerRealtimeEndpoint(datagramChannel);
    }

    @Override
    public void onWrite(final SelectionKey key) throws IOException {
        if (!(key.channel() instanceof DatagramChannel datagramChannel)) {
            return;
        }
        flushRealtimeUpdates(datagramChannel);
    }

    void close() {
        subscriptions.clear();
        Arrays.fill(pendingRealtimeUpdates, null);
        pendingRealtimeHead = 0;
        pendingRealtimeTail = 0;
        pendingRealtimeSize = 0;
        updatesWriteRegistered = false;
    }

    private void registerUpdatesWrites() throws IOException {
        if (updatesWriteRegistered) {
            return;
        }
        eventLoop.updateInterestOps(updatesDatagramChannel, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        updatesWriteRegistered = true;
    }

    private void registerRealtimeEndpoint(final DatagramChannel datagramChannel) throws IOException {
        udpReadBuffer.clear();
        final SocketAddress remoteAddress = datagramChannel.receive(udpReadBuffer);
        if (!(remoteAddress instanceof InetSocketAddress inetSocketAddress)) {
            return;
        }
        udpReadBuffer.flip();
        if (!udpReadBuffer.hasRemaining()) {
            return;
        }

        final long subscriptionId = parseSubscriptionId(udpReadBuffer);
        if (subscriptionId < 0L) {
            return;
        }
        final Subscription subscription = subscriptions.get(subscriptionId);
        if (subscription == null) {
            return;
        }
        subscription.updateCurrentInetSocketAddress(inetSocketAddress);
    }

    private void flushRealtimeUpdates(final DatagramChannel datagramChannel) throws IOException {
        while (true) {
            final byte[] updatePayload = pollRealtimeUpdate();
            if (updatePayload == null) {
                break;
            }
            for (final Subscription subscription : subscriptions.values()) {
                final InetSocketAddress endpoint = subscription.currentInetSocketAddress();
                if (endpoint == null) {
                    continue;
                }
                datagramChannel.send(ByteBuffer.wrap(updatePayload), endpoint);
            }
        }

        eventLoop.updateInterestOps(updatesDatagramChannel, SelectionKey.OP_READ);
        updatesWriteRegistered = false;
    }

    private void enqueueRealtimeUpdate(final byte[] payload) {
        if (pendingRealtimeSize == pendingRealtimeUpdates.length) {
            pendingRealtimeUpdates[pendingRealtimeHead] = null;
            pendingRealtimeHead = incrementRingIndex(pendingRealtimeHead);
            pendingRealtimeSize--;
        }
        pendingRealtimeUpdates[pendingRealtimeTail] = payload;
        pendingRealtimeTail = incrementRingIndex(pendingRealtimeTail);
        pendingRealtimeSize++;
    }

    private byte[] pollRealtimeUpdate() {
        if (pendingRealtimeSize == 0) {
            return null;
        }
        final byte[] payload = pendingRealtimeUpdates[pendingRealtimeHead];
        pendingRealtimeUpdates[pendingRealtimeHead] = null;
        pendingRealtimeHead = incrementRingIndex(pendingRealtimeHead);
        pendingRealtimeSize--;
        return payload;
    }

    private int incrementRingIndex(final int currentIndex) {
        final int next = currentIndex + 1;
        return next == pendingRealtimeUpdates.length ? 0 : next;
    }

    private static long parseSubscriptionId(final ByteBuffer payloadBuffer) {
        int startIndex = payloadBuffer.position();
        int endIndex = payloadBuffer.limit() - 1;

        while (startIndex <= endIndex && isAsciiWhitespace(payloadBuffer.get(startIndex))) {
            startIndex++;
        }
        while (endIndex >= startIndex && isAsciiWhitespace(payloadBuffer.get(endIndex))) {
            endIndex--;
        }
        if (startIndex > endIndex) {
            return -1L;
        }

        long parsedValue = 0L;
        for (int i = startIndex; i <= endIndex; i++) {
            final byte currentByte = payloadBuffer.get(i);
            if (currentByte < '0' || currentByte > '9') {
                return -1L;
            }
            parsedValue = parsedValue * 10L + (currentByte - '0');
            if (parsedValue < 0L) {
                return -1L;
            }
        }
        return parsedValue;
    }

    private static boolean isAsciiWhitespace(final byte value) {
        return value == ' ' || value == '\n' || value == '\r' || value == '\t';
    }
}
