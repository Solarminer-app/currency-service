package de.verdox.currencyrates.currencyrates.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.currencyrates.currencyrates.model.MiningNetworkSnapshot;
import de.verdox.currencyrates.currencyrates.repository.MiningNetworkSnapshotRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Collects and persists the public, coin-keyed C9 profitability snapshots. */
@Service
public class MiningNetworkDataService {
    private static final Logger LOGGER = Logger.getLogger(MiningNetworkDataService.class.getName());
    private static final List<String> COINS = List.of("bitcoin", "monero", "pearl", "ravencoin", "ethereumclassic", "conflux", "decred", "quantus");

    private final MiningNetworkSnapshotRepository repository;
    private final ObjectMapper mapper;
    private final HttpTextClient http;
    private final CoinGeckoPriceService prices;
    private final URI bitcoinStatsUrl;
    private final URI moneroNetworkUrl;
    private final String moneroBlockUrl;
    private final URI pearlStatsUrl;
    private final URI pearlPriceUrl;
    private final URI ravencoinStatsUrl;
    private final URI ethereumClassicStatsUrl;
    private final URI confluxStatsUrl;
    private final URI decredStatsUrl;
    private final URI decredSubsidyUrl;
    private final URI quantusStatsUrl;
    private final boolean refreshOnStartup;
    private final Duration staleAfter;

    public MiningNetworkDataService(
            MiningNetworkSnapshotRepository repository,
            ObjectMapper mapper,
            HttpTextClient http,
            CoinGeckoPriceService prices,
            @Value("${currency-rates.mining.bitcoin-stats-url:https://api.blockchain.info/stats}") URI bitcoinStatsUrl,
            @Value("${currency-rates.mining.monero-network-url:https://xmrchain.net/api/networkinfo}") URI moneroNetworkUrl,
            @Value("${currency-rates.mining.monero-block-url:https://xmrchain.net/api/block/%d}") String moneroBlockUrl,
            @Value("${currency-rates.mining.pearl-stats-url:https://pearlchain.live/api/explorer/stats}") URI pearlStatsUrl,
            @Value("${currency-rates.mining.pearl-price-url:https://pearlchain.live/api/explorer/price}") URI pearlPriceUrl,
            @Value("${currency-rates.mining.ravencoin-stats-url:https://rvn.2miners.com/api/stats}") URI ravencoinStatsUrl,
            @Value("${currency-rates.mining.ethereumclassic-stats-url:https://etc.2miners.com/api/stats}") URI ethereumClassicStatsUrl,
            @Value("${currency-rates.mining.conflux-stats-url:https://cfx.2miners.com/api/stats}") URI confluxStatsUrl,
            @Value("${currency-rates.mining.decred-stats-url:https://dcrdata.decred.org/api/block/best}") URI decredStatsUrl,
            @Value("${currency-rates.mining.decred-subsidy-url:https://dcrdata.decred.org/api/block/best/subsidy}") URI decredSubsidyUrl,
            @Value("${currency-rates.mining.quantus-stats-url:https://qtcscan.com/explorer-data.json}") URI quantusStatsUrl,
            @Value("${currency-rates.refresh-on-startup:true}") boolean refreshOnStartup,
            @Value("${currency-rates.mining.stale-after:PT2H}") Duration staleAfter) {
        this.repository = repository;
        this.mapper = mapper;
        this.http = http;
        this.prices = prices;
        this.bitcoinStatsUrl = bitcoinStatsUrl;
        this.moneroNetworkUrl = moneroNetworkUrl;
        this.moneroBlockUrl = moneroBlockUrl;
        this.pearlStatsUrl = pearlStatsUrl;
        this.pearlPriceUrl = pearlPriceUrl;
        this.ravencoinStatsUrl = ravencoinStatsUrl;
        this.ethereumClassicStatsUrl = ethereumClassicStatsUrl;
        this.confluxStatsUrl = confluxStatsUrl;
        this.decredStatsUrl = decredStatsUrl;
        this.decredSubsidyUrl = decredSubsidyUrl;
        this.quantusStatsUrl = quantusStatsUrl;
        this.refreshOnStartup = refreshOnStartup;
        this.staleAfter = staleAfter;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (refreshOnStartup) refreshAll();
    }

    @Scheduled(fixedDelayString = "${currency-rates.mining.refresh-ms:600000}", initialDelayString = "${currency-rates.mining.initial-delay-ms:60000}")
    public void scheduledRefresh() {
        refreshAll();
    }

    public synchronized void refreshAll() {
        Map<String, Double> currentPrices = prices.refreshCurrentPrices().orElseGet(Map::of);
        refresh("bitcoin", () -> parseBitcoin(json(bitcoinStatsUrl), currentPrices.getOrDefault("btc", 0.0), Instant.now()));
        refresh("monero", () -> fetchMonero(currentPrices.getOrDefault("xmr", 0.0)));
        refresh("pearl", this::fetchPearl);
        refresh("ravencoin", () -> fetchPoolStats("ravencoin", currentPrices.getOrDefault("rvn", 0.0), ravencoinStatsUrl));
        refresh("ethereumclassic", () -> fetchPoolStats("ethereumclassic", currentPrices.getOrDefault("etc", 0.0), ethereumClassicStatsUrl));
        refresh("conflux", () -> fetchPoolStats("conflux", currentPrices.getOrDefault("cfx", 0.0), confluxStatsUrl));
        refresh("decred", () -> fetchDecred(currentPrices.getOrDefault("dcr", 0.0)));
        refresh("quantus", () -> fetchQuantus(currentPrices.getOrDefault("qtc", 0.0)));
    }

    private void refresh(String coin, SnapshotFetcher fetcher) {
        try {
            MiningNetworkSnapshot snapshot = fetcher.fetch();
            validate(snapshot);
            repository.save(snapshot);
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING, "Could not refresh public mining data for " + coin + ": " + exception.getMessage());
        }
    }

    public List<PublicSnapshot> snapshots() {
        Map<String, MiningNetworkSnapshot> found = new LinkedHashMap<>();
        repository.findAll().forEach(snapshot -> found.put(snapshot.getCoin(), snapshot));
        List<PublicSnapshot> result = new ArrayList<>();
        for (String coin : COINS) {
            MiningNetworkSnapshot snapshot = found.get(coin);
            if (snapshot != null) result.add(toPublic(snapshot));
        }
        return result;
    }

    public Optional<PublicSnapshot> snapshot(String coinOrAlias) {
        String coin = canonicalCoin(coinOrAlias);
        return coin == null ? Optional.empty() : repository.findById(coin).map(this::toPublic);
    }

    static String canonicalCoin(String coinOrAlias) {
        if (coinOrAlias == null) return null;
        return switch (coinOrAlias.toLowerCase(Locale.ROOT)) {
            case "bitcoin", "btc" -> "bitcoin";
            case "monero", "xmr" -> "monero";
            case "pearl", "prl", "pearlhash" -> "pearl";
            case "ravencoin", "rvn" -> "ravencoin";
            case "ethereumclassic", "ethereum-classic", "etc" -> "ethereumclassic";
            case "conflux", "cfx" -> "conflux";
            case "decred", "dcr" -> "decred";
            case "quantus", "qtc" -> "quantus";
            default -> null;
        };
    }

    private PublicSnapshot toPublic(MiningNetworkSnapshot snapshot) {
        long ageSeconds = Math.max(0, Duration.between(snapshot.getCollectedAt(), Instant.now()).toSeconds());
        boolean stale = snapshot.isProviderStale() || ageSeconds > staleAfter.toSeconds();
        return new PublicSnapshot(snapshot.getCoin(), snapshot.getTicker(), snapshot.getAlgorithm(), true, stale,
                ageSeconds, snapshot.getNetworkHashrateHps(), snapshot.getDifficulty(),
                snapshot.getTargetBlockSeconds(), snapshot.getBlockReward(), snapshot.getPriceUsd(),
                snapshot.getCollectedAt(), snapshot.getNetworkSource(), snapshot.getPriceSource());
    }

    private MiningNetworkSnapshot fetchMonero(double priceUsd) throws Exception {
        JsonNode network = json(moneroNetworkUrl).path("data");
        long height = network.path("height").asLong();
        if (height < 1) throw new IllegalArgumentException("Monero height is missing");
        JsonNode block = json(URI.create(moneroBlockUrl.formatted(height - 1))).path("data");
        return parseMonero(network, block, priceUsd, Instant.now());
    }

    private MiningNetworkSnapshot fetchPearl() throws Exception {
        return parsePearl(json(pearlStatsUrl), json(pearlPriceUrl), Instant.now());
    }

    private MiningNetworkSnapshot fetchPoolStats(String coin, double priceUsd, URI statsUrl) throws Exception {
        return parsePoolStats(coin, json(statsUrl), priceUsd, Instant.now());
    }

    private MiningNetworkSnapshot fetchDecred(double priceUsd) throws Exception {
        JsonNode stats = json(decredStatsUrl);
        JsonNode subsidy = json(decredSubsidyUrl);
        return parseDecred(stats, subsidy, priceUsd, Instant.now());
    }

    private MiningNetworkSnapshot fetchQuantus(double priceUsd) throws Exception {
        return parseQuantus(json(quantusStatsUrl), priceUsd, Instant.now());
    }

    private JsonNode json(URI uri) throws Exception {
        return mapper.readTree(http.get(uri));
    }

    static MiningNetworkSnapshot parseMonero(JsonNode network, JsonNode block, double priceUsd, Instant collectedAt) {
        double reward = block.path("txs").path(0).path("xmr_outputs").asDouble() / 1_000_000_000_000.0;
        return new MiningNetworkSnapshot("monero", "XMR", "RandomX", network.path("hash_rate").asDouble(),
                network.path("difficulty").asDouble(), network.path("target").asDouble(), reward, priceUsd,
                collectedAt, "xmrchain.net", "CoinGecko", false);
    }

    static MiningNetworkSnapshot parseBitcoin(JsonNode stats, double priceUsd, Instant collectedAt) {
        long height = stats.path("n_blocks_total").asLong();
        long timestampMs = stats.path("timestamp").asLong();
        if (height <= 0 || timestampMs <= 0) throw new IllegalArgumentException("Incomplete Bitcoin stats");
        double effectivePrice = priceUsd > 0 ? priceUsd : stats.path("market_price_usd").asDouble();
        double reward = 50.0 / Math.pow(2.0, Math.floorDiv(height, 210_000L));
        Instant updated = Instant.ofEpochMilli(timestampMs);
        return new MiningNetworkSnapshot("bitcoin", "BTC", "SHA-256",
                stats.path("hash_rate").asDouble() * 1_000_000_000.0,
                stats.path("difficulty").asDouble(), 600.0, reward, effectivePrice, updated,
                "blockchain.com", priceUsd > 0 ? "CoinGecko" : "blockchain.com", false);
    }

    static MiningNetworkSnapshot parsePearl(JsonNode stats, JsonNode price, Instant collectedAt) {
        return new MiningNetworkSnapshot("pearl", "PRL", "PearlHash", stats.path("networkHashPs").asDouble(),
                stats.path("difficulty").asDouble(), stats.path("targetBlockSecs").asDouble(),
                stats.path("blockRewardPearl").asDouble(), price.path("price").asDouble(), collectedAt,
                "pearlchain.live", price.path("source").asText("pearlchain.live"), price.path("stale").asBoolean(false));
    }

    static MiningNetworkSnapshot parsePoolStats(String coin, JsonNode stats, double priceUsd, Instant collectedAt) {
        JsonNode node = stats.path("nodes").path(0);
        long height = node.path("height").asLong();
        double difficulty = node.path("difficulty").asDouble();
        double networkHashrate = node.path("networkhashps").asDouble();
        double targetSeconds = node.path("avgBlockTime").asDouble();
        if ("ravencoin".equals(coin)) {
            double reward = 5_000.0 / Math.pow(2.0, Math.floorDiv(height, 2_100_000L));
            return new MiningNetworkSnapshot(coin, "RVN", "KAWPOW", networkHashrate, difficulty,
                    targetSeconds, reward, priceUsd, collectedAt, "rvn.2miners.com", "CoinGecko", false);
        }
        if ("ethereumclassic".equals(coin)) {
            double reward = 5.0 * Math.pow(0.8, Math.floorDiv(height, 5_000_000L));
            return new MiningNetworkSnapshot(coin, "ETC", "ETCHash", networkHashrate, difficulty,
                    targetSeconds, reward, priceUsd, collectedAt, "etc.2miners.com", "CoinGecko", false);
        }
        if ("conflux".equals(coin)) {
            // 2Miners exposes the live PoW block reward when the pool node supports it.
            // Do not substitute a hard-coded subsidy: Conflux's reward schedule changes.
            double reward = node.path("blockReward").asDouble();
            return new MiningNetworkSnapshot(coin, "CFX", "Octopus", networkHashrate, difficulty,
                    targetSeconds, reward, priceUsd, collectedAt, "cfx.2miners.com", "CoinGecko", false);
        }
        throw new IllegalArgumentException("Unsupported pool-stats coin: " + coin);
    }

    static MiningNetworkSnapshot parseDecred(JsonNode stats, JsonNode subsidy, double priceUsd, Instant collectedAt) {
        double difficulty = stats.path("diff").asDouble();
        long blockTime = stats.path("time").asLong();
        if (blockTime <= 0) throw new IllegalArgumentException("Decred block timestamp is missing");
        double reward = subsidy.path("work_reward").asDouble() / 100_000_000.0;
        return new MiningNetworkSnapshot("decred", "DCR", "BLAKE3",
                difficulty * 4_294_967_296.0 / 300.0, difficulty,
                300.0, reward, priceUsd, Instant.ofEpochSecond(blockTime),
                "dcrdata.decred.org", "CoinGecko", false);
    }

    static MiningNetworkSnapshot parseQuantus(JsonNode stats, double priceUsd, Instant collectedAt) {
        if (stats.path("schema").asInt(-1) != 1) throw new IllegalArgumentException("Unsupported QTCScan schema");
        long updatedSeconds = stats.path("updated_at").asLong(0);
        double difficulty = stats.path("difficulty").asDouble(0);
        double networkHashrate = stats.path("windows").path("1h").path("hashrate").asDouble(0);
        double targetSeconds = stats.path("target_block_time").asDouble(0);
        double reward = stats.path("reward").asDouble(0);
        if (updatedSeconds <= 0 || !(difficulty > 0) || !(networkHashrate > 0)
                || !(targetSeconds > 0) || !(reward > 0) || !(priceUsd > 0)) {
            throw new IllegalArgumentException("Incomplete Quantus network snapshot");
        }
        Instant providerUpdated = Instant.ofEpochSecond(updatedSeconds);
        boolean providerStale = Duration.between(providerUpdated, collectedAt).compareTo(Duration.ofMinutes(3)) > 0;
        return new MiningNetworkSnapshot("quantus", "QTC", "QPoW (Poseidon2)", networkHashrate,
                difficulty, targetSeconds, reward, priceUsd, providerUpdated,
                "qtcscan.com/explorer-data.json (1h estimated hashrate)", "CoinGecko", providerStale);
    }

    private static void validate(MiningNetworkSnapshot value) {
        if (!(value.getNetworkHashrateHps() > 0) || !(value.getDifficulty() > 0)
                || !(value.getTargetBlockSeconds() > 0) || !(value.getBlockReward() > 0)
                || !(value.getPriceUsd() > 0) || value.getCollectedAt() == null) {
            throw new IllegalArgumentException("Incomplete mining-network snapshot");
        }
    }

    @FunctionalInterface
    private interface SnapshotFetcher {
        MiningNetworkSnapshot fetch() throws Exception;
    }

    public record PublicSnapshot(String coin, String ticker, String algorithm, boolean available, boolean stale,
                                 long ageSeconds, double networkHashrateHps, double difficulty,
                                 double targetBlockSeconds, double blockReward, double priceUsd,
                                 Instant updatedAt, String networkSource, String priceSource) {
    }
}
