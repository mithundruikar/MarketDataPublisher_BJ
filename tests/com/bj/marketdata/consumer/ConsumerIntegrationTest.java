package com.bj.marketdata.consumer;

import com.bj.marketdata.MarketDataPublisherApplication;
import com.bj.marketdata.entity.InstrumentUpdateType;
import com.bj.marketdata.entity.MarketDataRawUpdate;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
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
        final ConsumerLogonResponse logonResponse;

        try (final Socket socket = new Socket(
                wiring.consumerConnectionHandler().boundLogonAddress().getHostString(),
                wiring.consumerConnectionHandler().boundLogonAddress().getPort());
             final OutputStreamWriter writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
             final BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(
                     socket.getInputStream(),
                     StandardCharsets.UTF_8
             ))) {
            writer.write(clientId + "\n");
            writer.flush();
            final String response = reader.readLine();
            assertNotNull(response, "expected logon response");
            logonResponse = ConsumerLogonResponse.fromWireMessage(response);
        }

        try (final DatagramSocket udpClient = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"))) {
            udpClient.setSoTimeout(2_000);
            final byte[] registrationPayload = (logonResponse.subscriptionId() + "\n").getBytes(StandardCharsets.UTF_8);
            final DatagramPacket registrationPacket = new DatagramPacket(
                    registrationPayload,
                    registrationPayload.length,
                    InetAddress.getByName(logonResponse.updatesHost()),
                    logonResponse.updatesPort()
            );
            udpClient.send(registrationPacket);

            final long registerDeadlineNanos = System.nanoTime() + 2_000_000_000L;
            while (wiring.consumerConnectionHandler().realtimeEndpointCount() < 1 && System.nanoTime() < registerDeadlineNanos) {
                Thread.sleep(10L);
            }
            assertEquals(1, wiring.consumerConnectionHandler().realtimeEndpointCount(), "expected one registered realtime endpoint");

            final MarketDataRawUpdate update =
                    new MarketDataRawUpdate(9_999L, 1_700_000_000_000L, "integration-test", "INTEGRATION_ONLY", InstrumentUpdateType.BASE_RATE, 4.1234);
            wiring.derivedMarketDataService().applyUpdate(update);

            final long receiveDeadlineNanos = System.nanoTime() + 2_000_000_000L;
            while (true) {
                final byte[] receiveBuffer = new byte[256];
                final DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                udpClient.receive(receivePacket);
                final String received = new String(
                        receivePacket.getData(),
                        receivePacket.getOffset(),
                        receivePacket.getLength(),
                        StandardCharsets.UTF_8
                );
                if (received.startsWith("1700000000000,INTEGRATION_ONLY,")) {
                    assertEquals("1700000000000,INTEGRATION_ONLY,4.1234\n", received, "unexpected realtime update payload");
                    break;
                }
                if (System.nanoTime() >= receiveDeadlineNanos) {
                    throw new AssertionError("Did not receive derived integration payload before deadline");
                }
            }
        } finally {
            wiring.consumerConnectionHandler().close();
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
}
