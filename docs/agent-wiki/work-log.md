# Agent work log

## 2026-10-07 — BTC price unit and C9 BTC/DCR/QTC completeness

- Live read-only public host check: current production `/coin-prices` had BTC but no DCR/QTC; `/mining-networks` had XMR/PRL/RVN/ETC only. Current source was ahead of production for DCR/QTC, so source fixes alone do not change the deployed responses.
- Fixed the legacy Bitcoin `q/24hrprice` conversion (the provider already returns USD/BTC) and added guarded V3 correction for stored, unmistakably scaled values. Added canonical BTC C9 snapshot from Blockchain.com aggregate stats, with GH/s→H/s and height-based base subsidy. Replaced unresolvable `dcr.2miners.com` with reachable dcrdata latest-block difficulty and PoW-only subsidy; QTC retains the existing CoinGecko/QTCScan collectors. Missing/incomplete snapshots still retain last-good rows and expire by the existing stale policy.
- Provider checks: CoinGecko simple-price returned positive BTC/DCR/QTC values; Blockchain.com stats, QTCScan schema-1, and dcrdata best block/subsidy responded. No production release/deployment, post-deployment public response, real mining share or payout was verified. Full local Gradle test suite passed on JDK 21 (19 tests), including the V3 migration against H2; proxy and PC-Agent suites passed separately (25 and 77 tests).

## 2026-10-06 — Conflux network and price snapshot

- Added CFX (`conflux`, alias `cfx`) to the public coin-keyed mining snapshot.
  It reads aggregate network data and the current PoW block reward from the
  2Miners stats API and adds CoinGecko's `conflux-token` USD quote to the
  existing batched price request. The existing validation and persistence
  rules reject missing/zero reward data and preserve the last complete row.
- The public snapshot uses H/s, seconds, native CFX per block and USD/CFX, with
  source labels and the standard two-hour stale bound. PC-Agent forecast
  publication remains unchanged until there is a managed Octopus miner path.
- Verification: not run. Provider parsing, persistence, aliases and public
  response tests remain required. No deployment or live provider response was
  checked in this change.

## 2026-10-06 — Decred network and price snapshot

- Added canonical `decred`/DCR price and network data. CoinGecko supplies the
  USD quote, 2Miners supplies network hashrate/difficulty/block interval, and
  dcrdata's latest subsidy endpoint supplies `work_reward` in atoms. Conversion
  is 1e8 atoms per DCR, avoiding use of the combined PoW/PoS/treasury subsidy.
- PC-Agent's forecast reads the public snapshot and matches `blake3_decred` GPU
  workers. The DCR forecast is gross; no pool fees or payout credits are
  represented. The PC-Agent miner path remains blocked by the absent fee target.
- Verification: not run. Provider fixture parsing, alias/public response,
  persistence and miner/proxy protocol tests remain required; no live provider
  response or accepted share was checked.

## 2026-10-04 — Standalone repository migration

- Migrated the current Currency Service source, tests, Flyway migrations and
  public deployment documentation from `Solar-Miner-Node/currency-rates` into
  the dedicated `currency-service` repository.
- Added an independent Java 21 Gradle build, repository-owned version and image
  metadata, a complete wrapper, Flyway runtime support, OCI build configuration
  and Dockerfile.
- Added pull-request verification and tag-driven amd64/arm64 Docker publishing.
  Stable versions update `latest`; prerelease versions update `beta`.
- Runtime endpoints, the public hostname and the Docker image repository remain
  unchanged. Production deployment and MariaDB restart persistence remain
  rollout gates.
- Verification: all 16 tests pass under GraalVM Java 21; `clean check bootJar`,
  `printVersion` and `printDockerImage` pass. The executable JAR starts with an
  H2 database, applies Flyway V1 and V2, returns `[]` from the empty
  `mining-networks` endpoint and serves OpenAPI. Container construction was not
  executed because the local Docker socket is unavailable to this user.
## 2026-10-06 — Quantus (QTC) network snapshot source

- Added CoinGecko's `quantus` price ID and QTCScan's public schema-1 network
  snapshot. Canonical key `quantus`, alias `qtc`; H/s, seconds and QTC units.
- `updated_at` is kept as the observation time and data older than three
  minutes is provider-stale. Missing schema/metrics/reward/price fails the
  snapshot validation, preserving the latest complete persisted row.
- Verification not run. No live provider response or deployment was checked.
  QTC profitability display remains subject to provider parsing/persistence/
  public contract verification.

## 2026-10-06 — Repository ownership made explicit (leftover Node copy)

- `AGENTS.md` and `docs/agent-wiki/README.md` now state unambiguously that this
  service is an independent Git repository (`Solarminer-app/currency-service`)
  and that `Solar-Miner-Node/currency-rates/` is a leftover copy from the
  2026-10-04 migration: never edited, tested, released, documented or used as
  evidence of current behavior.
- Same pass updated the consumer-side entry points: workspace `AGENTS.md`,
  `NEW-MINING-COIN-GUIDE.md`, `Solar-Miner-Node/AGENTS.md`, the Node agent wiki
  (responsibility map, doc catalog, feature workflow, `currency-data.md` banner),
  the Node `README.md`, `admin-portal/AGENTS.md` and encyclopedia
  `00`/`03`/`07`/`08` (C9 owner and release path), plus a warning file at
  `Solar-Miner-Node/currency-rates/AGENTS.md`.
- Evidence: `git rev-parse --show-toplevel` here returns
  `.../Solarminer/currency-service`; `git remote -v` shows
  `https://github.com/Solarminer-app/currency-service`. `diff -rq` of the two
  `src/` trees shows three differing files (`OpenApiConfiguration`,
  `CoinGeckoPriceService`, `MiningNetworkDataService`), all ahead in this
  repository — the Node copy has no `conflux`, `decred` or `quantus` collectors
  and lacks the mining-network OpenAPI tag.
- Open cleanup gate (not performed, owner decision required): the Node copy is
  still `include("currency-rates")` in `Solar-Miner-Node/settings.gradle.kts`,
  still built by `docker-compose.node-sim.yml` from
  `./Solar-Miner-Node/currency-rates/build/libs`, and still releasable through
  `Solar-Miner-Node/.github/workflows/docker-deploy-currency_rates_service.yml`
  on `currency-rates-v*` tags. Until that wiring is removed, a build or image
  from the Node repository says nothing about this service.
