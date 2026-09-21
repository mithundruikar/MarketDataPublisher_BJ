# MarketDataPublisher Skeleton

Initial skeleton for a latency-sensitive market data publisher service.

## Base Package

`com.bj.marketdata`

## Modules

1. `service`
   - `DerivedMarketDataService`: holds derived market data state.
   - Exposes APIs to update and read state.

2. `consumer`
   - `Subscription`: consumer subscription model.
   - Downstream publication handling contracts.

3. `multiplexer`
   - NIO selector/event-loop contract.
   - Readable/writable socket handling contract.

4. `source`
   - Source abstraction for raw updates.
   - `MarketUpdateRawFileSource` placeholder for file-based updates.
   - `UdpSource` placeholder for UDP-based updates.

5. `admin`
   - Admin/tapping entry points for observability.
   - Progress tracking, rejected-update counts, subscription-level visibility contracts.

## Current Status

- Skeleton only.
- No business or networking implementation yet.
- Interfaces and placeholder classes are created for step-by-step implementation.
- Maven project with JUnit 5 tests (unit + integration separation).

## Test Skeleton

Test folders are created under `tests/com/bj/marketdata`:

- `service`
- `consumer`
- `multiplexer`
- `source`
- `admin`
- `main` (bootstrap test)

## Main Test + Properties

- Main test entry: `com.bj.marketdata.main.MarketDataPublisherBootstrapTest`
- Properties file: `tests/resources/market-data-publisher-test.properties`

## Build and Test (Maven)

```bash
mvn test
mvn verify
```

`mvn test` runs low-level unit tests.  
`mvn verify` runs both unit tests and integration tests (`*IntegrationTest`).
