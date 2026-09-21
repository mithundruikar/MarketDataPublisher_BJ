package com.bj.marketdata;

import com.bj.marketdata.consumer.CsvLoggerConsumer;
import com.bj.marketdata.consumer.ConsumerConnectionHandler;
import com.bj.marketdata.multiplexer.EventLoop;
import com.bj.marketdata.service.DerivedMarketDataService;
import com.bj.marketdata.source.UdpSource;
import com.bj.marketdata.source.file.MarketRawUpdateFileSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Entry point for the market-data publisher process.
 *
 * <p>Supported CLI arguments:
 * <ul>
 *   <li>{@code --raw_updates <path>} (mandatory): CSV input file containing raw market updates.</li>
 *   <li>{@code --consumer.logon <tcp://host:port>} (optional): TCP endpoint for consumer logon.</li>
 *   <li>{@code --consumer.update <udp://host:port>} (optional): UDP endpoint for consumer realtime updates.</li>
 * </ul>
 *
 * <p>If consumer connectivity is enabled, both optional consumer arguments must be provided together.
 * If they are omitted, updates are logged to console through {@code CsvLoggerConsumer}.
 *
 * <p>The application always sets {@code rawUpdate.source} to {@code DEFAULT} when parsing CLI arguments.
 *
 * <p>Example (mandatory only):
 * <pre>{@code
 * java com.bj.marketdata.MarketDataPublisherApplication \
 *   --raw_updates /data/market_inputs.csv
 * }</pre>
 *
 * <p>Meaning: starts file-driven publishing immediately and writes derived CSV updates to application logs.
 *
 * <p>Example (mandatory + optional consumer endpoints):
 * <pre>{@code
 * java com.bj.marketdata.MarketDataPublisherApplication \
 *   --raw_updates /data/market_inputs.csv \
 *   --consumer.logon tcp://127.0.0.1:7001 \
 *   --consumer.update udp://127.0.0.1:7002
 * }</pre>
 *
 * <p>Meaning: waits for a consumer logon on TCP, then publishes derived updates to registered UDP endpoints.
 */
public final class MarketDataPublisherApplication {
    public static final String RAW_UPDATE_SOURCE_PROPERTY = "rawUpdate.source";
    public static final String RAW_UPDATE_FILE_PROPERTY = "rawUpdate.file";
    public static final String RAW_UPDATE_UDP_CONNECT_PROPERTY = "rawUpdate.udp.connect";
    public static final String CONSUMER_CONNECT_LOGON_PROPERTY = "consumer.connect.logon";
    public static final String CONSUMER_CONNECT_UPDATES_PROPERTY = "consumer.connect.updates";
    public static final String DEFAULT_RAW_UPDATE_SOURCE = "DEFAULT";

    private static final InetSocketAddress DEFAULT_UDP_BIND = new InetSocketAddress("127.0.0.1", 9001);
    private static final String ARG_RAW_UPDATES = "--raw_updates";
    private static final String ARG_CONSUMER_LOGON = "--consumer.logon";
    private static final String ARG_CONSUMER_UPDATE = "--consumer.update";
    private static final DateTimeFormatter LOG_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final Logger LOGGER = Logger.getLogger(MarketDataPublisherApplication.class.getName());

    public static MarketDataPublisherApplication application;

    private final Formatter consoleLogFormatter = new Formatter() {
        @Override
        public String format(final LogRecord record) {
            final String timestamp = LOG_TIMESTAMP_FORMAT.format(
                    Instant.ofEpochMilli(record.getMillis()).atZone(ZoneId.systemDefault()));
            return timestamp + " "
                    + shortLoggerName(record.getLoggerName()) + " "
                    + record.getLevel().getName() + " "
                    + formatMessage(record)
                    + System.lineSeparator();
        }
    };

    private boolean loggingConfigured;
    private Properties runtimeProperties;
    private Wiring runtimeWiring;

    public static void main(final String[] args) throws Exception {
        final MarketDataPublisherApplication createdApplication = new MarketDataPublisherApplication();
        application = createdApplication;
        createdApplication.start(args);
    }

    public void start(final String[] args) throws Exception {
        configureConsoleLogging();
        final Properties properties;
        try {
            properties = propertiesFromArgs(args);
        } catch (final IllegalArgumentException exception) {
            LOGGER.severe(exception.getMessage());
            LOGGER.info(usage());
            return;
        }
        this.runtimeProperties = properties;
        this.runtimeWiring = wire(properties);
        Runtime.getRuntime().addShutdownHook(new Thread(this::closeQuietly, "market-data-publisher-shutdown"));
        runtimeWiring.eventLoop().run();
    }

    public Wiring wire(final Path propertiesPath) throws IOException {
        Objects.requireNonNull(propertiesPath, "propertiesPath");
        final Properties properties = new Properties();
        try (final InputStream inputStream = Files.newInputStream(propertiesPath)) {
            properties.load(inputStream);
        }
        return wire(properties);
    }

    public Wiring wire(final Properties properties) throws IOException {
        Objects.requireNonNull(properties, "properties");
        final String sourceName = readRequiredProperty(properties, RAW_UPDATE_SOURCE_PROPERTY);
        final String udpConnect = readOptionalProperty(properties, RAW_UPDATE_UDP_CONNECT_PROPERTY);
        final String consumerLogon = readOptionalProperty(properties, CONSUMER_CONNECT_LOGON_PROPERTY);
        final String consumerUpdates = readOptionalProperty(properties, CONSUMER_CONNECT_UPDATES_PROPERTY);
        validateConsumerEndpointConfiguration(consumerLogon, consumerUpdates);

        final EventLoop eventLoop = new EventLoop();
        final DerivedMarketDataService derivedMarketDataService = new DerivedMarketDataService();
        final MarketRawUpdateFileSource fileSource =
                new MarketRawUpdateFileSource(sourceName, Path.of(readRequiredProperty(properties, RAW_UPDATE_FILE_PROPERTY)), eventLoop);
        final ConsumerConnectionHandler consumerConnectionHandler = createOptionalConsumerConnectionHandler(
                eventLoop,
                properties,
                fileSource,
                consumerLogon != null
        );

        final UdpSource udpSource = udpConnect == null
                ? null
                : new UdpSource(sourceName, udpConnect, DEFAULT_UDP_BIND, eventLoop);

        fileSource.addListener(derivedMarketDataService);
        if (consumerConnectionHandler != null) {
            derivedMarketDataService.addDerivedMarketDataUpdateListener(consumerConnectionHandler.derivedUpdateListener());
        } else {
            derivedMarketDataService.addDerivedMarketDataUpdateListener(new CsvLoggerConsumer());
        }
        final Wiring wiring = new Wiring(eventLoop, derivedMarketDataService, fileSource, udpSource, consumerConnectionHandler);
        this.runtimeWiring = wiring;
        return wiring;
    }

    public void setReady(final boolean ready) {
        if (runtimeWiring == null) {
            throw new IllegalStateException("Application wiring is not initialized");
        }
        runtimeWiring.fileSource().setReady(ready);
    }

    public Properties runtimeProperties() {
        return runtimeProperties;
    }

    public Wiring runtimeWiring() {
        return runtimeWiring;
    }

    private ConsumerConnectionHandler createOptionalConsumerConnectionHandler(
            final EventLoop eventLoop,
            final Properties properties,
            final MarketRawUpdateFileSource fileSource,
            final boolean consumerConfigured
    ) throws IOException {
        if (!consumerConfigured) {
            fileSource.setReady(true);
            return null;
        }
        fileSource.setReady(false);
        return new ConsumerConnectionHandler(eventLoop, properties);
    }

    /**
     * Parses named CLI arguments into runtime properties used by wiring.
     *
     * <p>Required: {@code --raw_updates}
     * <br>Optional: {@code --consumer.logon}, {@code --consumer.update}
     *
     * @param args command-line arguments in {@code --name value} pairs
     * @return populated runtime properties, including {@code rawUpdate.source=DEFAULT}
     * @throws IllegalArgumentException when arguments are malformed, unknown, or missing required values
     */
    Properties propertiesFromArgs(final String[] args) {
        Objects.requireNonNull(args, "args");
        if ((args.length & 1) != 0) {
            throw new IllegalArgumentException("Arguments must be provided as --name value pairs.");
        }
        final Map<String, String> argumentsByName = new HashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            final String argumentName = args[i];
            final String argumentValue = args[i + 1];
            if (!isSupportedArgument(argumentName)) {
                throw new IllegalArgumentException("Unknown argument: " + argumentName);
            }
            argumentsByName.put(argumentName, argumentValue);
        }

        final String rawUpdatesPath = trimToNull(argumentsByName.get(ARG_RAW_UPDATES));
        if (rawUpdatesPath == null) {
            throw new IllegalArgumentException("Missing required argument: " + ARG_RAW_UPDATES);
        }
        final String consumerLogon = trimToNull(argumentsByName.get(ARG_CONSUMER_LOGON));
        final String consumerUpdate = trimToNull(argumentsByName.get(ARG_CONSUMER_UPDATE));

        final Properties properties = new Properties();
        properties.setProperty(RAW_UPDATE_SOURCE_PROPERTY, DEFAULT_RAW_UPDATE_SOURCE);
        properties.setProperty(RAW_UPDATE_FILE_PROPERTY, rawUpdatesPath);
        if (consumerLogon != null) {
            properties.setProperty(CONSUMER_CONNECT_LOGON_PROPERTY, consumerLogon);
        }
        if (consumerUpdate != null) {
            properties.setProperty(CONSUMER_CONNECT_UPDATES_PROPERTY, consumerUpdate);
        }
        return properties;
    }

    String usage() {
        return "Usage: java com.bj.marketdata.MarketDataPublisherApplication"
                + " --raw_updates <path> (this is the path to the raw updates file)"
                + " [--consumer.logon <tcp://host:port>] (this is the address to which consumers will connect to logon)"
                + " [--consumer.update <udp://host:port>] (this is the address to which consumers will connect to receive updates)";
    }

    public record Wiring(
            EventLoop eventLoop,
            DerivedMarketDataService derivedMarketDataService,
            MarketRawUpdateFileSource fileSource,
            UdpSource udpSource,
            ConsumerConnectionHandler consumerConnectionHandler
    ) {
    }

    private String readRequiredProperty(final Properties properties, final String key) {
        final String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required property: " + key);
        }
        return value.trim();
    }

    private String readOptionalProperty(final Properties properties, final String key) {
        final String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private boolean isSupportedArgument(final String argumentName) {
        return ARG_RAW_UPDATES.equals(argumentName)
                || ARG_CONSUMER_LOGON.equals(argumentName)
                || ARG_CONSUMER_UPDATE.equals(argumentName);
    }

    private String trimToNull(final String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void closeQuietly() {
        final Wiring wiring = runtimeWiring;
        if (wiring == null) {
            return;
        }
        try {
            final ConsumerConnectionHandler consumerConnectionHandler = wiring.consumerConnectionHandler();
            if (consumerConnectionHandler != null) {
                consumerConnectionHandler.close();
            }
        } catch (final Exception ignored) {
        }
        try {
            wiring.eventLoop().close();
        } catch (final Exception ignored) {
        }
    }



    private void validateConsumerEndpointConfiguration(
            final String consumerLogon,
            final String consumerUpdates
    ) {
        if ((consumerLogon == null) == (consumerUpdates == null)) {
            return;
        }
        throw new IllegalArgumentException(
                "Both " + CONSUMER_CONNECT_LOGON_PROPERTY + " and " + CONSUMER_CONNECT_UPDATES_PROPERTY
                        + " must be set together when enabling consumer connectivity."
        );
    }

    private synchronized void configureConsoleLogging() {
        if (loggingConfigured) {
            return;
        }
        final Logger rootLogger = Logger.getLogger("");
        boolean consoleHandlerFound = false;
        for (final Handler handler : rootLogger.getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                handler.setFormatter(consoleLogFormatter);
                handler.setLevel(Level.INFO);
                consoleHandlerFound = true;
            }
        }
        if (!consoleHandlerFound) {
            final ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setLevel(Level.INFO);
            consoleHandler.setFormatter(consoleLogFormatter);
            rootLogger.addHandler(consoleHandler);
        }
        rootLogger.setLevel(Level.INFO);
        loggingConfigured = true;
    }

    private String shortLoggerName(final String loggerName) {
        if (loggerName == null || loggerName.isBlank()) {
            return "root";
        }
        final int dotIndex = loggerName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == loggerName.length() - 1) {
            return loggerName;
        }
        return loggerName.substring(dotIndex + 1);
    }
}
