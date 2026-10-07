package de.verdox.currencyrates.currencyrates.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MiningNetworkDataServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Instant now = Instant.parse("2026-10-04T12:00:00Z");

    @Test
    void parsesMoneroUnitsAndCoinbaseReward() throws Exception {
        var network = mapper.readTree("""
                {"difficulty":"798134995218","hash_rate":6651124960,"target":120}
                """);
        var block = mapper.readTree("""
                {"txs":[{"coinbase":true,"xmr_outputs":606768460000}]}
                """);

        var result = MiningNetworkDataService.parseMonero(network, block, 542.46, now);

        assertThat(result.getCoin()).isEqualTo("monero");
        assertThat(result.getNetworkHashrateHps()).isEqualTo(6_651_124_960d);
        assertThat(result.getBlockReward()).isEqualTo(0.60676846d);
        assertThat(result.getPriceUsd()).isEqualTo(542.46d);
    }

    @Test
    void parsesBitcoinStatsInHashesPerSecondAndUsdPerCoin() throws Exception {
        var stats = mapper.readTree("""
                {"timestamp":1791362000000,"market_price_usd":84000.0,
                 "hash_rate":1.075E12,"difficulty":1.327E14,"n_blocks_total":970314}
                """);
        var result = MiningNetworkDataService.parseBitcoin(stats, 84100.0, now);
        assertThat(result.getCoin()).isEqualTo("bitcoin");
        assertThat(result.getNetworkHashrateHps()).isEqualTo(1.075E21);
        assertThat(result.getBlockReward()).isEqualTo(3.125);
        assertThat(result.getPriceUsd()).isEqualTo(84100.0);
    }

    @Test
    void parsesDecredExplorerDifficultyAndPowOnlyReward() throws Exception {
        var stats = mapper.readTree("{\"diff\":421888.6,\"time\":1791361822}");
        var subsidy = mapper.readTree("{\"work_reward\":5100425,\"total\":510042578}");
        var result = MiningNetworkDataService.parseDecred(stats, subsidy, 18.04, now);
        assertThat(result.getNetworkHashrateHps()).isCloseTo(421888.6 * Math.pow(2, 32) / 300,
                org.assertj.core.data.Offset.offset(0.01));
        assertThat(result.getBlockReward()).isEqualTo(0.05100425);
        assertThat(result.getPriceUsd()).isEqualTo(18.04);
    }

    @Test
    void parsesPearlProviderFreshness() throws Exception {
        var stats = mapper.readTree("""
                {"difficulty":33564279.039,"networkHashPs":5.01364992850243E19,
                 "targetBlockSecs":194,"blockRewardPearl":2283.1608057463836}
                """);
        var price = mapper.readTree("{\"price\":1.013,\"source\":\"coinpaprika\",\"stale\":true}");

        var result = MiningNetworkDataService.parsePearl(stats, price, now);

        assertThat(result.getCoin()).isEqualTo("pearl");
        assertThat(result.isProviderStale()).isTrue();
        assertThat(result.getPriceSource()).isEqualTo("coinpaprika");
    }

    @Test
    void calculatesRavencoinAndEthereumClassicRewardsFromHeight() throws Exception {
        var ravencoin = mapper.readTree("""
                {"nodes":[{"avgBlockTime":"57.57","difficulty":"11091.6893",
                  "height":"4566758","networkhashps":"771818804685.0007"}]}
                """);
        var ethereumClassic = mapper.readTree("""
                {"nodes":[{"avgBlockTime":"14.00","difficulty":"1964747158816436",
                  "height":"25471049","networkhashps":"140334070841501"}]}
                """);

        var rvn = MiningNetworkDataService.parsePoolStats("ravencoin", ravencoin, 0.05, now);
        var etc = MiningNetworkDataService.parsePoolStats("ethereumclassic", ethereumClassic, 18.0, now);

        assertThat(rvn.getBlockReward()).isEqualTo(1250.0);
        assertThat(rvn.getAlgorithm()).isEqualTo("KAWPOW");
        assertThat(etc.getBlockReward()).isCloseTo(1.6384, org.assertj.core.data.Offset.offset(0.0000001));
        assertThat(etc.getAlgorithm()).isEqualTo("ETCHash");
    }

    @Test
    void resolvesOnlyDocumentedCanonicalAliases() {
        assertThat(MiningNetworkDataService.canonicalCoin("XMR")).isEqualTo("monero");
        assertThat(MiningNetworkDataService.canonicalCoin("BTC")).isEqualTo("bitcoin");
        assertThat(MiningNetworkDataService.canonicalCoin("PRL")).isEqualTo("pearl");
        assertThat(MiningNetworkDataService.canonicalCoin("RVN")).isEqualTo("ravencoin");
        assertThat(MiningNetworkDataService.canonicalCoin("ETC")).isEqualTo("ethereumclassic");
        assertThat(MiningNetworkDataService.canonicalCoin("ethereum-classic")).isEqualTo("ethereumclassic");
        assertThat(MiningNetworkDataService.canonicalCoin("unknown")).isNull();
    }
}
