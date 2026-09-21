package com.bj.marketdata.consumer;

import com.bj.marketdata.MarketDataPublisherApplication;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConsumerIntegrationTest {
    @Test
    void shouldFlowRealtimeUpdatesAfterConsumerLogon() throws Exception {
        final Properties properties = loadProperties();
        final MarketDataPublisherApplication.Wiring wiring = MarketDataPublisherApplication.wire(properties);
        final Thread eventLoopThread = new Thread(wiring.eventLoop(), "consumer-integration-event-loop");
        eventLoopThread.start();

        final String clientId = "client-integration-1";
        final InetSocketAddress updatesEndpoint;

        try (final Socket socket = new Socket(
                wiring.consumerHandler().boundLogonAddress().getHostString(),
                wiring.consumerHandler().boundLogonAddress().getPort());
             final OutputStreamWriter writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
             final BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(
                     socket.getInputStream(),
                     StandardCharsets.UTF_8
             ))) {
            writer.write(clientId + "\n");
            writer.flush();
            final String response = reader.readLine();
            assertNotNull(response, "expected logon response");
            updatesEndpoint = parseHostPort(response);
        }

        try (final DatagramSocket udpClient = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"))) {
            udpClient.setSoTimeout(2_000);
            final byte[] registrationPayload = (clientId + "\n").getBytes(StandardCharsets.UTF_8);
            final DatagramPacket registrationPacket = new DatagramPacket(
                    registrationPayload,
                    registrationPayload.length,
                    updatesEndpoint.getAddress(),
                    updatesEndpoint.getPort()
            );
            udpClient.send(registrationPacket);

            final long registerDeadlineNanos = System.nanoTime() + 2_000_000_000L;
            while (wiring.consumerHandler().realtimeEndpointCount() < 1 && System.nanoTime() < registerDeadlineNanos) {
                Thread.sleep(10L);
            }
            assertEquals(1, wiring.consumerHandler().realtimeEndpointCount(), "expected one registered realtime endpoint");

            final String updateMessage = "ALPHA,4.1234";
            wiring.consumerHandler().publishRealtimeUpdate(updateMessage);

            final byte[] receiveBuffer = new byte[256];
            final DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            udpClient.receive(receivePacket);
            final String received = new String(
                    receivePacket.getData(),
                    receivePacket.getOffset(),
                    receivePacket.getLength(),
                    StandardCharsets.UTF_8
            );
            assertEquals(updateMessage, received, "unexpected realtime update payload");
        } finally {
            wiring.consumerHandler().close();
            wiring.eventLoop().close();
            eventLoopThread.join(2_000);
        }
    }

    private static Properties loadProperties() throws Exception {
        final Properties properties = new Properties();
        final Path propertiesPath = Path.of(ConsumerIntegrationTest.class.getClassLoader()
                .getResource("market-data-publisher-test.properties")
                .toURI());
        try (final InputStream inputStream = Files.newInputStream(propertiesPath)) {
            properties.load(inputStream);
        }
        return properties;
    }

    private static InetSocketAddress parseHostPort(final String hostPort) {
        final int colonIdx = hostPort.lastIndexOf(':');
        if (colonIdx <= 0 || colonIdx >= hostPort.length() - 1) {
            throw new IllegalArgumentException("Invalid host:port response: " + hostPort);
        }
        final String host = hostPort.substring(0, colonIdx);
        final int port = Integer.parseInt(hostPort.substring(colonIdx + 1));
        return new InetSocketAddress(host, port);
    }
}
