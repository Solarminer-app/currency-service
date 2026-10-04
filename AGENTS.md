# Agent entry point — SolarMiner Currency Service

Read `docs/agent-wiki/README.md` and inspect Git status before changing code.
Preserve existing work. For Java work, use focused IntelliJ IDEA MCP navigation
when that server is available; otherwise use repository and shell tools.

This repository owns the public, versioned currency and mining-network data
service at `https://currency.solarminer.app/api/v1/public/**`. Only aggregated,
read-only market and network snapshots belong here. Wallets, workers, pool
credentials, referrals, account balances, individual telemetry and miner
control do not.

Keep existing `/api/v1/public/**` payloads backward-compatible. An incompatible
change requires a new versioned route. A new profitability-relevant coin needs
both price and mining-network collection, explicit units and precision,
persisted timestamps, bounded stale/unavailable behavior, provider tests,
persistence tests and public-contract tests.

Record durable architecture and contract findings in `docs/agent-wiki/` and
completed work with verification evidence in `docs/agent-wiki/work-log.md`.
When this repository is used inside the SolarMiner workspace, also follow the
workspace `NEW-MINING-COIN-GUIDE.md` and update every affected consumer's
contract documentation.
