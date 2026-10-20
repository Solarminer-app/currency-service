package de.verdox.currencyrates.currencyrates.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BitcoinMiningDataFetcherTest {

    @Test
    void publishesOneCompleteSnapshotAfterAllProviderResponsesAreAvailable() throws Exception {
        Map<String, String> responses = Map.of(
                "https://blockchain.info/q/getdifficulty", "123456",
                "https://blockchain.info/q/hashrate", "5000",
                "https://blockchain.info/q/24hrprice", "100.00",
                "https://blockchain.info/q/bcperblock", "6.25",
                "https://api.blockchair.com/bitcoin/stats", "{\"data\":{\"average_transaction_fee_24h\":42}}"
        );
        BitcoinMiningDataFetcher fetcher = new BitcoinMiningDataFetcher(
                new ObjectMapper(),
                uri -> responses.get(uri.toString())
        );

        BitcoinMiningDataFetcher.BitcoinMiningSnapshot snapshot = fetcher.query();

        assertEquals(123456, snapshot.miningDifficulty());
        assertEquals(5.0, snapshot.hashRateInThs());
        assertEquals(100.0, snapshot.priceInDollar());
        assertEquals(625_000_000, snapshot.blockSubsidy());
        assertEquals(42, snapshot.averageTxPrice24h());
    }

    @Test
    void doesNotReturnAPartialSnapshotWhenAProviderFails() {
        HttpTextClient failingClient = uri -> {
            if (uri.equals(URI.create("https://api.blockchair.com/bitcoin/stats"))) {
                throw new IOException("provider unavailable");
            }
            return "1";
        };
        BitcoinMiningDataFetcher fetcher = new BitcoinMiningDataFetcher(new ObjectMapper(), failingClient);

        assertThrows(IOException.class, fetcher::query);
    }

    @Test
    void usesCoinGeckoDirectlyWhenTheIndependentBitcoinPriceProviderFails() throws Exception {
        HttpTextClient client = uri -> switch (uri.toString()) {
            case "https://blockchain.info/q/getdifficulty" -> "123456";
            case "https://blockchain.info/q/hashrate" -> "5000";
            case "https://blockchain.info/q/24hrprice" -> throw new IOException("price unavailable");
            case "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd" -> "{\"bitcoin\":{\"usd\":98765.0}}";
            case "https://blockchain.info/q/bcperblock" -> "3.125";
            case "https://api.blockchair.com/bitcoin/stats" -> "{\"data\":{\"average_transaction_fee_24h\":42}}";
            default -> throw new IOException("unexpected request: " + uri);
        };

        var snapshot = new BitcoinMiningDataFetcher(new ObjectMapper(), client).query();

        assertEquals(98765.0, snapshot.priceInDollar());
    }
}
