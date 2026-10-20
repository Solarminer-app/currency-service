package de.verdox.currencyrates.currencyrates.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.currencyrates.currencyrates.model.DailyUsdRates;
import de.verdox.currencyrates.currencyrates.repository.BitcoinNetworkStatsRepository;
import de.verdox.currencyrates.currencyrates.repository.DailyUsdRatesRepository;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

class DataGathererServiceTest {

    @Test
    void historicalRateRequestUsesTheRequestedDateAsProviderVersion() {
        URI uri = DataGathererService.exchangeRatesUri(LocalDate.of(2025, 2, 3));

        assertEquals(
                "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@2025-02-03/v1/currencies/usd.json",
                uri.toString()
        );
    }

    @Test
    void currentRateRequestUsesLatestProviderVersion() {
        assertEquals(
                "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json",
                DataGathererService.exchangeRatesUri(null).toString()
        );
    }

    @Test
    void currentRateFallbackUsesTheDocumentedCloudflareEndpoint() {
        assertEquals(
                "https://latest.currency-api.pages.dev/v1/currencies/usd.json",
                DataGathererService.exchangeRatesFallbackUri(null).toString()
        );
    }

    @Test
    void historicalFetchPersistsTheRequestedDateFromAFakeUpstream() {
        AtomicReference<URI> requestedUri = new AtomicReference<>();
        DailyUsdRatesRepository ratesRepository = mock(DailyUsdRatesRepository.class);
        when(ratesRepository.save(any(DailyUsdRates.class))).thenAnswer(invocation -> invocation.getArgument(0));
        DataGathererService service = service(
                ratesRepository,
                mock(BitcoinMiningDataFetcher.class),
                uri -> {
                    requestedUri.set(uri);
                    return "{\"usd\":{\"eur\":0.9}}";
                }
        );

        DailyUsdRates saved = service.fetchAndSaveForDate(LocalDate.of(2025, 2, 3));

        assertEquals(LocalDate.of(2025, 2, 3), saved.getDate());
        assertEquals(DataGathererService.exchangeRatesUri(LocalDate.of(2025, 2, 3)), requestedUri.get());
    }

    @Test
    void fetchUsesProviderFallbackWhenJsDelivrIsUnavailable() {
        List<URI> requestedUris = new ArrayList<>();
        DailyUsdRatesRepository ratesRepository = mock(DailyUsdRatesRepository.class);
        when(ratesRepository.save(any(DailyUsdRates.class))).thenAnswer(invocation -> invocation.getArgument(0));
        LocalDate date = LocalDate.of(2025, 2, 3);
        DataGathererService service = service(
                ratesRepository,
                mock(BitcoinMiningDataFetcher.class),
                uri -> {
                    requestedUris.add(uri);
                    if (uri.equals(DataGathererService.exchangeRatesUri(date))) {
                        throw new IOException("jsDelivr unavailable");
                    }
                    return "{\"date\":\"2025-02-03\",\"usd\":{\"eur\":0.9}}";
                }
        );

        DailyUsdRates saved = service.fetchAndSaveForDate(date);

        assertEquals(date, saved.getDate());
        assertEquals(List.of(
                DataGathererService.exchangeRatesUri(date),
                DataGathererService.exchangeRatesFallbackUri(date)
        ), requestedUris);
    }

    @Test
    void fetchRejectsSnapshotWhoseProviderDateDoesNotMatchTheRequestedDate() {
        DailyUsdRatesRepository ratesRepository = mock(DailyUsdRatesRepository.class);
        DataGathererService service = service(
                ratesRepository,
                mock(BitcoinMiningDataFetcher.class),
                uri -> "{\"date\":\"2025-02-02\",\"usd\":{\"eur\":0.9}}"
        );

        assertNull(service.fetchAndSaveForDate(LocalDate.of(2025, 2, 3)));
        verifyNoInteractions(ratesRepository);
    }

    @Test
    void failedRefreshClearsThePriorBitcoinSnapshotBeforePersistence() throws Exception {
        BitcoinMiningDataFetcher bitcoinFetcher = mock(BitcoinMiningDataFetcher.class);
        when(bitcoinFetcher.query())
                .thenReturn(new BitcoinMiningDataFetcher.BitcoinMiningSnapshot(1, 2, 3, 4, 5))
                .thenThrow(new IOException("upstream unavailable"));
        BitcoinNetworkStatsRepository bitcoinRepository = mock(BitcoinNetworkStatsRepository.class);
        DailyUsdRatesRepository ratesRepository = mock(DailyUsdRatesRepository.class);
        DataGathererService service = new DataGathererService(
                bitcoinRepository,
                ratesRepository,
                new ObjectMapper(),
                mock(CoinGeckoPriceService.class),
                bitcoinFetcher,
                uri -> "{\"usd\":{\"eur\":0.9}}",
                true
        );

        service.scheduledFetch();
        service.scheduledFetch();

        assertFalse(service.saveDailyStatsToDatabase());
        verifyNoInteractions(bitcoinRepository, ratesRepository);
    }

    @Test
    void persistsLatestStoredCurrencyRatesWhenBothProviderEndpointsAreUnavailable() throws Exception {
        LocalDate yesterday = LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1);
        DailyUsdRates previousRates = new DailyUsdRates(yesterday, java.util.Map.of("eur", 0.92));
        DailyUsdRatesRepository ratesRepository = mock(DailyUsdRatesRepository.class);
        when(ratesRepository.findFirstByDateLessThanEqualOrderByDateDesc(any(LocalDate.class)))
                .thenReturn(java.util.Optional.of(previousRates));
        BitcoinMiningDataFetcher bitcoinFetcher = mock(BitcoinMiningDataFetcher.class);
        when(bitcoinFetcher.query()).thenReturn(new BitcoinMiningDataFetcher.BitcoinMiningSnapshot(1, 2, 3, 4, 5));
        BitcoinNetworkStatsRepository bitcoinRepository = mock(BitcoinNetworkStatsRepository.class);
        DataGathererService service = new DataGathererService(
                bitcoinRepository, ratesRepository, new ObjectMapper(), mock(CoinGeckoPriceService.class),
                bitcoinFetcher, uri -> { throw new IOException("provider unavailable"); }, true
        );

        service.scheduledFetch();
        assertEquals(true, service.saveDailyStatsToDatabase());

        verify(ratesRepository).save(org.mockito.ArgumentMatchers.argThat(rate ->
                yesterday.equals(rate.getDate()) && rate.getRates().get("eur") == 0.92));
        verify(ratesRepository, never()).save(org.mockito.ArgumentMatchers.argThat(rate ->
                LocalDate.now(java.time.ZoneOffset.UTC).equals(rate.getDate())));
    }

    private DataGathererService service(DailyUsdRatesRepository ratesRepository,
                                        BitcoinMiningDataFetcher bitcoinFetcher,
                                        HttpTextClient httpClient) {
        return new DataGathererService(
                mock(BitcoinNetworkStatsRepository.class),
                ratesRepository,
                new ObjectMapper(),
                mock(CoinGeckoPriceService.class),
                bitcoinFetcher,
                httpClient,
                true
        );
    }
}
