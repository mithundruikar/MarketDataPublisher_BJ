package com.bj.marketdata.consumer;

import com.bj.marketdata.multiplexer.EventLoop;
import com.bj.marketdata.multiplexer.IOHandler;
import com.bj.marketdata.service.DerivedMarketDataUpdateListener;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public final class ConsumerConnectionHandler implements IOHandler, AutoCloseable {
    private static final String LOGON_PROPERTY = "consumer.connect.logon";
    private static final String UPDATES_PROPERTY = "consumer.connect.updates";
    private static final int REQUEST_BUFFER_SIZE_BYTES = 512;

    private final EventLoop eventLoop;
    private final String consumerConnectLogon;
    private final String consumerConnectUpdates;
    private final InetSocketAddress configuredLogonAddress;
    private final InetSocketAddress configuredUpdatesAddress;
    private final Map<SocketChannel, ByteBuffer> requestBuffersByClient = new HashMap<>();
    private final Map<SocketChannel, ByteBuffer> responseBuffersByClient = new HashMap<>();

    private long nextSubscriptionId = 1L;
    private ServerSocketChannel logonServerChannel;
    private DatagramChannel updatesDatagramChannel;
    private SubscriptionHandler subscriptionHandler;
    private InetSocketAddress boundLogonAddress;
    private InetSocketAddress boundUpdatesAddress;

    public ConsumerConnectionHandler(final EventLoop eventLoop, final Properties properties) throws IOException {
        this.eventLoop = Objects.requireNonNull(eventLoop, "eventLoop");
        Objects.requireNonNull(properties, "properties");

        this.consumerConnectLogon = readRequiredProperty(properties, LOGON_PROPERTY);
        this.consumerConnectUpdates = readRequiredProperty(properties, UPDATES_PROPERTY);
        this.configuredLogonAddress = parseSocketAddress(this.consumerConnectLogon, LOGON_PROPERTY);
        this.configuredUpdatesAddress = parseSocketAddress(this.consumerConnectUpdates, UPDATES_PROPERTY);

        bindAndRegister();
    }

    public InetSocketAddress boundLogonAddress() {
        return boundLogonAddress;
    }

    public InetSocketAddress boundUpdatesAddress() {
        return boundUpdatesAddress;
    }

    public int subscriptionCount() {
        return subscriptionHandler.subscriptionCount();
    }

    public int realtimeEndpointCount() {
        return subscriptionHandler.realtimeEndpointCount();
    }

    public void publishRealtimeUpdate(final String payload) {
        subscriptionHandler.publishRealtimeUpdate(payload);
    }

    public DerivedMarketDataUpdateListener derivedUpdateListener() {
        return subscriptionHandler;
    }

    @Override
    public void onAccept(final SelectionKey key) throws IOException {
        if (!(key.channel() instanceof ServerSocketChannel serverSocketChannel)) {
            return;
        }
        final SocketChannel clientChannel = serverSocketChannel.accept();
        if (clientChannel == null) {
            return;
        }

        requestBuffersByClient.put(clientChannel, ByteBuffer.allocate(REQUEST_BUFFER_SIZE_BYTES));
        eventLoop.register(clientChannel, SelectionKey.OP_READ, this);
    }

    @Override
    public void onRead(final SelectionKey key) throws IOException {
        if (!(key.channel() instanceof SocketChannel clientChannel)) {
            return;
        }

        final ByteBuffer requestBuffer =
                requestBuffersByClient.computeIfAbsent(clientChannel, ignored -> ByteBuffer.allocate(REQUEST_BUFFER_SIZE_BYTES));
        final int readBytes = clientChannel.read(requestBuffer);
        if (readBytes < 0) {
            closeClient(clientChannel, key);
            return;
        }
        if (readBytes == 0) {
            return;
        }

        final String requestLine = extractLine(requestBuffer);
        if (requestLine == null) {
            return;
        }

        final ConsumerLogonRequest logonRequest;
        try {
            logonRequest = ConsumerLogonRequest.fromWire(requestLine);
        } catch (IllegalArgumentException ex) {
            closeClient(clientChannel, key);
            return;
        }

        final long subscriptionId = nextSubscriptionId++;
        subscriptionHandler.registerSubscription(logonRequest.clientId(), subscriptionId);

        final ConsumerLogonResponse logonResponse = new ConsumerLogonResponse(
                subscriptionId,
                boundUpdatesAddress.getHostString(),
                boundUpdatesAddress.getPort()
        );
        responseBuffersByClient.put(clientChannel, ByteBuffer.wrap(logonResponse.toWireMessage().getBytes(StandardCharsets.UTF_8)));
        key.interestOps(SelectionKey.OP_WRITE);
    }

    @Override
    public void onWrite(final SelectionKey key) throws IOException {
        if (key.channel() instanceof SocketChannel clientChannel) {
            final ByteBuffer responseBuffer = responseBuffersByClient.get(clientChannel);
            if (responseBuffer == null) {
                key.interestOps(SelectionKey.OP_READ);
                return;
            }

            clientChannel.write(responseBuffer);
            if (!responseBuffer.hasRemaining()) {
                closeClient(clientChannel, key);
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (logonServerChannel != null) {
            logonServerChannel.close();
            logonServerChannel = null;
        }
        if (updatesDatagramChannel != null) {
            updatesDatagramChannel.close();
            updatesDatagramChannel = null;
        }
        if (subscriptionHandler != null) {
            subscriptionHandler.close();
            subscriptionHandler = null;
        }
        for (final SocketChannel clientChannel : requestBuffersByClient.keySet()) {
            clientChannel.close();
        }
        requestBuffersByClient.clear();
        responseBuffersByClient.clear();
    }

    private void bindAndRegister() throws IOException {
        this.logonServerChannel = ServerSocketChannel.open();
        this.logonServerChannel.bind(configuredLogonAddress);
        this.boundLogonAddress = (InetSocketAddress) logonServerChannel.getLocalAddress();
        eventLoop.register(logonServerChannel, SelectionKey.OP_ACCEPT, this);

        this.updatesDatagramChannel = DatagramChannel.open();
        this.updatesDatagramChannel.bind(configuredUpdatesAddress);
        this.boundUpdatesAddress = (InetSocketAddress) updatesDatagramChannel.getLocalAddress();
        this.subscriptionHandler = new SubscriptionHandler(eventLoop, updatesDatagramChannel);
        eventLoop.register(updatesDatagramChannel, SelectionKey.OP_READ, subscriptionHandler);
    }

    private void closeClient(final SocketChannel clientChannel, final SelectionKey key) throws IOException {
        key.cancel();
        requestBuffersByClient.remove(clientChannel);
        responseBuffersByClient.remove(clientChannel);
        clientChannel.close();
    }

    private static String extractLine(final ByteBuffer buffer) {
        buffer.flip();
        final int startIndex = buffer.position();
        for (int i = startIndex; i < buffer.limit(); i++) {
            if (buffer.get(i) == '\n') {
                int contentStart = startIndex;
                int contentEnd = i;
                if (contentEnd > contentStart && buffer.get(contentEnd - 1) == '\r') {
                    contentEnd--;
                }
                while (contentStart < contentEnd && isAsciiWhitespace(buffer.get(contentStart))) {
                    contentStart++;
                }
                while (contentEnd > contentStart && isAsciiWhitespace(buffer.get(contentEnd - 1))) {
                    contentEnd--;
                }
                final String line = contentStart >= contentEnd
                        ? ""
                        : new String(buffer.array(), contentStart, contentEnd - contentStart, StandardCharsets.UTF_8);
                buffer.position(i + 1);
                buffer.compact();
                return line;
            }
        }
        buffer.compact();
        return null;
    }

    private static boolean isAsciiWhitespace(final byte value) {
        return value == ' ' || value == '\n' || value == '\r' || value == '\t';
    }

    private static String readRequiredProperty(final Properties properties, final String key) {
        final String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required property: " + key);
        }
        return value.trim();
    }

    private static InetSocketAddress parseSocketAddress(final String rawAddress, final String propertyName) {
        final String trimmed = rawAddress.trim();
        final int schemeIdx = trimmed.indexOf("://");
        final String normalized = schemeIdx >= 0 ? trimmed.substring(schemeIdx + 3) : trimmed;
        final int colonIdx = normalized.lastIndexOf(':');
        if (colonIdx <= 0 || colonIdx >= normalized.length() - 1) {
            throw new IllegalArgumentException("Invalid socket address for " + propertyName + ": " + rawAddress);
        }
        final String host = normalized.substring(0, colonIdx).trim();
        final String portText = normalized.substring(colonIdx + 1).trim();
        final int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid port for " + propertyName + ": " + rawAddress, ex);
        }
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("Port out of range for " + propertyName + ": " + rawAddress);
        }
        return new InetSocketAddress(host, port);
    }
}
