# SolarMiner Currency Service

Public, read-only market and mining-network data for SolarMiner Nodes and
other SolarMiner products. The production API is served from
`https://currency.solarminer.app/api/v1/public/**`.

The service collects exchange rates, coin prices, Bitcoin network data and
coin-keyed mining-network snapshots. It persists the latest complete data and
keeps the last successful snapshot when an upstream provider is unavailable.
Wallets, workers, pool credentials, referral data and individual miner
telemetry do not belong in this service.

## Requirements

- Java 21
- Docker, only when building or running the container image
- MariaDB in production; the default local profile uses an H2 file database

## Build and test

```bash
./gradlew clean check
./gradlew bootRun
```

The local API and Swagger UI are available on port `8080` by default. The
`dev` profile uses port `8081`:

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

## Versioning and releases

Release metadata lives in `gradle.properties`:

```properties
version=1.1.1
artifact=currency-rates
dockerImage=verdox/currency-rates-api
```

`./gradlew printVersion` and `./gradlew printDockerImage` expose these values
to GitHub Actions. A tag must match the configured version exactly:

- `v1.2.0` publishes `verdox/currency-rates-api:1.2.0` and updates `latest`.
- `v1.2.0-beta.1` publishes that immutable version and updates `beta`.

Pull requests and pushes to `main` run the complete test suite. Release builds
also run the suite before publishing amd64 and arm64 images.

## Configuration

The production datasource is configured through:

- `MYSQL_URL`
- `MYSQL_USER`
- `MYSQL_PASSWORD`

Without these values the service uses its local H2 store. See
[PUBLIC-DEPLOYMENT.md](PUBLIC-DEPLOYMENT.md) for the central Traefik and
MariaDB deployment and [the currency data contract](docs/agent-wiki/currency-data.md)
for the public data contract.

## Main public endpoints

- `GET /api/v1/public/bitcoin-stats`
- `GET /api/v1/public/exchange-rates`
- `GET /api/v1/public/exchange-rates/convert`
- `GET /api/v1/public/coin-prices`
- `GET /api/v1/public/mining-networks`
- `GET /api/v1/public/mining-networks/{coin}`

Incompatible response changes require a new versioned route. Additive fields
and new coin entries must remain safe for existing consumers.
