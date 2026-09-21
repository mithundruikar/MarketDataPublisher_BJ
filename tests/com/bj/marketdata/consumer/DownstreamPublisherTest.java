package com.bj.marketdata.consumer;

import com.bj.marketdata.MarketDataPublisherApplication;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownstreamPublisherTest {
    @Test
    void shouldHandleConsumerLogonAndRespondWithUpdatesEndpoint() throws Exception {
        final Properties properties = loadProperties();
        final MarketDataPublisherApplication application = new MarketDataPublisherApplication();
        final MarketDataPublisherApplication.Wiring wiring = application.wire(properties);
        final Thread eventLoopThread = new Thread(wiring.eventLoop(), "downstream-publisher-event-loop");
        eventLoopThread.start();

        try (final Socket socket = new Socket(
                wiring.consumerConnectionHandler().boundLogonAddress().getHostString(),
                wiring.consumerConnectionHandler().boundLogonAddress().getPort());
             final OutputStreamWriter writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
             final BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(
                     socket.getInputStream(),
                     StandardCharsets.UTF_8
             ))) {
            writer.write("client-1\n");
            writer.flush();

            final String response = reader.readLine();
            assertNotNull(response, "expected logon response");
            final ConsumerLogonResponse logonResponse = ConsumerLogonResponse.fromWireMessage(response);
            assertEquals(
                    wiring.consumerConnectionHandler().boundUpdatesAddress().getHostString(),
                    logonResponse.updatesHost(),
                    "unexpected logon response updates host"
            );
            assertEquals(
                    wiring.consumerConnectionHandler().boundUpdatesAddress().getPort(),
                    logonResponse.updatesPort(),
                    "unexpected logon response with udp updates endpoint"
            );
            assertTrue(logonResponse.subscriptionId() > 0L, "expected positive subscription id");

            assertEquals(1, wiring.consumerConnectionHandler().subscriptionCount(), "expected one subscription after logon");
        } finally {
            wiring.consumerConnectionHandler().close();
            wiring.eventLoop().close();
            eventLoopThread.join(2_000);
        }
    }

    private static Properties loadProperties() throws Exception {
        final Properties properties = new Properties();
        final Path propertiesPath = Path.of(DownstreamPublisherTest.class.getClassLoader()
                .getResource("market-data-publisher-test.properties")
                .toURI());
        try (final InputStream inputStream = Files.newInputStream(propertiesPath)) {
            properties.load(inputStream);
        }
        return properties;
    }
}
