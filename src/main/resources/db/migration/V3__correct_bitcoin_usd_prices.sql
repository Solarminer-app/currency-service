-- The legacy collector treated blockchain.info/q/24hrprice (already USD/BTC)
-- as satoshis and divided it by 1e8. Correct only unmistakably scaled rows;
-- leave historical provider rows that were already in USD untouched.
UPDATE bitcoin_network_stats
SET price_in_dollar = price_in_dollar * 100000000
WHERE date >= '2013-01-01'
  AND price_in_dollar > 0
  AND price_in_dollar < 1;
