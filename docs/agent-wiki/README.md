# Currency Service agent wiki

This repository owns SolarMiner's central public currency and mining-network
data service. Source code and tests are authoritative; deployment documents
describe required rollout checks but are not evidence that a rollout happened.

## Responsibility map

| Area | Responsibility |
| --- | --- |
| `controller/` | Backward-compatible `/api/v1/public/**` HTTP contract |
| `service/` | Provider collection, validation, refresh and stale-data behavior |
| `model/`, `repository/` | Persisted daily values and latest coin snapshots |
| `db/migration/` | Ordered MariaDB/H2 schema migrations |
| `.github/workflows/` | Verification and versioned multi-architecture releases |
| `PUBLIC-DEPLOYMENT.md` | Central Traefik/MariaDB rollout and acceptance checks |

## Durable records

- [Currency and mining-network contract](currency-data.md)
- [Work log](work-log.md)
- [Public deployment](../../PUBLIC-DEPLOYMENT.md)

## Change rules

1. Inspect Git status and the affected controller, service and tests.
2. Keep `/api/v1/public/**` backward-compatible; version incompatible changes.
3. Never persist incomplete provider results or substitute zero for unavailable
   data.
4. Add provider parsing, persistence, stale/failure and public-contract tests.
5. Verify the consumer contract in every affected repository.
6. Record completed contract work and evidence in the work log.
