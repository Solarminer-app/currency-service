# Public currency and mining-network data

Checked against this service and the PC-Agent consumer on 2026-10-04. This is the implementation record for contract C9; deployment at `currency.solarminer.app` remains a separate rollout step.

## Public contract

`GET /api/v1/public/mining-networks` returns the latest complete snapshots. `GET /api/v1/public/mining-networks/{coin}` accepts the canonical key and documented ticker aliases. Unknown or never-successfully-collected coins return 404. Fields and units are:

| Field | Meaning |
| --- | --- |
| `coin`, `ticker`, `algorithm` | Canonical identity and display metadata |
| `networkHashrateHps` | Network hashrate in hashes per second |
| `difficulty` | Provider-reported chain difficulty as a floating-point value |
| `targetBlockSeconds` | Expected or observed block interval in seconds |
| `blockReward` | Base native-coin reward per block; pool fees, transaction fees, uncle rewards and MEV are excluded |
| `priceUsd` | USD per native coin |
| `updatedAt`, `ageSeconds` | Persisted collection instant and response-time age |
| `networkSource`, `priceSource` | Upstream provenance |
| `available`, `stale` | Complete snapshot and bounded freshness state |

Successful snapshots replace the row keyed by canonical coin. Failed or incomplete refreshes never write zeroes and retain the prior row. The collector refreshes every ten minutes; an otherwise valid row is marked stale after two hours. `coin-prices` remains backward-compatible and adds `rvn` and `etc`; current PRL falls back to Pearl's public price endpoint because CoinGecko's configured `pearl-research` ID did not return a quote in the 2026-10-04 live probe.

## Coin providers and calculations

| Canonical key / ticker | Network source | Price source | Reward and interval |
| --- | --- | --- | --- |
| `bitcoin` / BTC | [Blockchain.com stats](https://www.blockchain.com/explorer/api/charts_api), hash rate converted from GH/s to H/s | CoinGecko `bitcoin`, falling back to Blockchain.com's USD/BTC quote | 50 BTC halved each 210,000 blocks; 600-second target. The legacy `bitcoin-stats` collector separately uses `q/24hrprice`, whose response is already USD/BTC. |
| `monero` / XMR | [`xmrchain.net` network and latest block](https://xmrchain.net/api/networkinfo) | [CoinGecko](https://docs.coingecko.com/reference/simple-price) | Latest coinbase outputs converted from atomic units; provider target seconds |
| `pearl` / PRL | [`pearlchain.live/api/explorer/stats`](https://pearlchain.live/api/explorer/stats) | [Pearl price response](https://pearlchain.live/api/explorer/price), currently identifying CoinPaprika | Provider reward, target and provider stale flag |
| `ravencoin` / RVN | [2Miners public RVN stats](https://rvn.2miners.com/api/stats) | [CoinGecko](https://docs.coingecko.com/reference/simple-price) | 5,000 RVN shifted each 2,100,000-block era; provider average block seconds. The interval and initial subsidy are defined in [Ravencoin Core `chainparams.cpp`](https://github.com/RavenProject/Ravencoin/blob/master/src/chainparams.cpp). |
| `ethereumclassic` / ETC | [2Miners public ETC stats](https://etc.2miners.com/api/stats) | [CoinGecko](https://docs.coingecko.com/reference/simple-price) | 5 ETC × 0.8 per 5,000,000-block era under [ECIP-1017](https://ecips.ethereumclassic.org/ECIPs/ecip-1017); provider average block seconds. Transaction fees, uncle rewards and MEV are intentionally absent. |
| `conflux` / CFX | [2Miners public CFX stats](https://cfx.2miners.com/api/stats) | [CoinGecko](https://docs.coingecko.com/reference/simple-price) (`conflux-token`) | Provider network hashrate, difficulty, average block time and live `blockReward`; a missing reward rejects the refresh rather than using a stale subsidy constant. Algorithm: Octopus. |
| `decred` / DCR | [dcrdata latest block](https://dcrdata.decred.org/api/block/best) | [CoinGecko](https://docs.coingecko.com/reference/simple-price) (`decred`) | Difficulty from dcrdata; estimated H/s = difficulty × 2^32 / 300 seconds. Current PoW `work_reward` in atoms from [dcrdata subsidy](https://dcrdata.decred.org/api/block/best/subsidy) divided by 1e8. Total PoW+PoS+treasury subsidy is not used. Algorithm: BLAKE3. |

The 2Miners values are public aggregated network statistics, not user pool balances or proof of SolarMiner share credit. The PC-Agent uses one central request only, caches it for ten minutes and publishes forecasts for RandomX, PearlHash, KAWPOW, ETCHash and BLAKE3. Its source list retains `currency.solarminer.app` and upstream provenance. CFX is collected by this service but is deliberately not exposed in PC-Agent forecasts until a managed Octopus miner path exists. DCR's work reward is taken separately from dcrdata because Decred divides subsidy between PoW, PoS and treasury.

The local Node consumer treats the agent forecast array as additive and ignores coin keys that are not yet registered in the Node's complete `MiningCoin` path. Consequently RVN/ETC forecasts are available in the PC-Agent without falsely enabling those coins in Node profitability automation; their full Node registration still follows the new-coin guide.

## 2026-10-07 market-data repair and deployment boundary

The public host was checked read-only: `/coin-prices` returned BTC/XMR/PRL/RVN/ETC but no DCR/QTC; `/mining-networks` returned only XMR/PRL/RVN/ETC. That deployed service is behind this repository and cannot supply the prepared DCR/QTC rows until independently released and deployed. CoinGecko's live simple-price API returned positive BTC/DCR/QTC quotes; QTCScan's schema-1 feed and dcrdata's latest block/subsidy endpoints were reachable, while `dcr.2miners.com` did not resolve. The collector therefore adds BTC to C9 and replaces DCR's dead 2Miners dependency with dcrdata. The Bitcoin legacy daily collector no longer divides the already-USD `q/24hrprice` value by 1e8; migration V3 repairs unmistakably mis-scaled persisted rows from 2013 onward. No production deployment or post-deployment API response is evidenced here.

## Coin-guide scope

This work fills only the currency/network-data section of the workspace's new-coin guide and the related consumer/contract tests. It does not change wallet formats, miner processes, Stratum, proxy ports, fee targets, pool accounting, portal provisioning, hardware capability, deployment flags or rollout status. Those steps are therefore non-applicable to this changeset, not completed by it. Pearl retains its existing production record; the PC-Agent repository owns the remaining RVN/ETC mining rollout evidence.

Verification covers provider fixture parsing, reward-era calculations, alias resolution, JPA persistence, public JSON shape and a PC-Agent HTTP consumer fixture. All 16 Currency-Service and 50 PC-Agent tests pass on Java 21; the root app compiles and its focused `MiningCoinTest` passes. A temporary H2-backed live service returned four fresh network snapshots, all five price keys and the ETC alias detail response. The production host returned a Traefik 404 for the new and existing public routes before rollout, so public reachability and persistence after central MariaDB/container restart remain open gates.
# QTC / Quantus (prepared 2026-10-06)

The source collector now recognizes canonical `quantus` and alias `qtc`.
CoinGecko's API coin ID is `quantus`. Network snapshots come from QTCScan's
public schema-1 `explorer-data.json`: one-hour estimated network hashrate (H/s),
difficulty, `target_block_time` (seconds) and `reward` (QTC/block). The API's
`updated_at` Unix timestamp is retained; observations older than three minutes
are marked provider-stale, and malformed/missing fields do not overwrite the
last complete persisted snapshot. The public C9 response schema is unchanged.

The hashrate is an explorer estimate from adjacent canonical block difficulty,
not a directly measured network counter. QTCScan is an independent explorer,
not operated by Quantus. The 2026-10-07 follow-up observed a live schema-1
QTCScan response and a positive CoinGecko `quantus` quote. Persisted QTC rows
and a deployed public endpoint response are still unverified; do not describe
QTC as an operational profitability feed until those checks pass.
