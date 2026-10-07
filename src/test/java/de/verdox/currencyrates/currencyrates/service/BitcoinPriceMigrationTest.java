package de.verdox.currencyrates.currencyrates.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BitcoinPriceMigrationTest {
    @Test
    void repairsOnlyMisScaledPersistedBitcoinPrices() throws Exception {
        String url = "jdbc:h2:mem:bitcoin-price-migration;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration")
                .target("2").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO bitcoin_network_stats VALUES ('2026-10-07',1,1,0.00084035,1,1)");
            statement.executeUpdate("INSERT INTO bitcoin_network_stats VALUES ('2026-10-06',1,1,84035,1,1)");
        }
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT date, price_in_dollar FROM bitcoin_network_stats ORDER BY date")) {
            rows.next(); assertEquals(84035d, rows.getDouble(2), 0.0001);
            rows.next(); assertEquals(84035d, rows.getDouble(2), 0.0001);
        }
    }
}
