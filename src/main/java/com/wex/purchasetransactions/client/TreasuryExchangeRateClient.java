package com.wex.purchasetransactions.client;

import com.wex.purchasetransactions.dto.ExchangeRateResult;
import com.wex.purchasetransactions.dto.TreasuryApiResponse;
import com.wex.purchasetransactions.dto.TreasuryApiResponse.TreasuryRateRecord;
import com.wex.purchasetransactions.exception.TreasuryApiUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class TreasuryExchangeRateClient {

    private static final Logger log = LoggerFactory.getLogger(TreasuryExchangeRateClient.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int LOOKBACK_MONTHS = 6;

    private final RestClient restClient;
    private final String baseUrl;

    public TreasuryExchangeRateClient(RestClient treasuryRestClient, @Value("${treasury.api.base-url}") String baseUrl) {
        this.restClient = treasuryRestClient;
        this.baseUrl = baseUrl;
    }

    @Cacheable(value = "exchangeRates", key = "#currency + ':' + #transactionDate")
    @CircuitBreaker(name = "treasuryApi", fallbackMethod = "getExchangeRateFallback")
    public Optional<ExchangeRateResult> getExchangeRate(String currency, LocalDate transactionDate) {
        LocalDate windowStart = transactionDate.minusMonths(LOOKBACK_MONTHS);

        String filter = "country_currency_desc:eq:" + currency
                + ",record_date:gte:" + windowStart.format(DATE_FMT)
                + ",record_date:lte:" + transactionDate.format(DATE_FMT);

        log.debug("Fetching exchange rate for currency={} transactionDate={}", currency, transactionDate);

        String uri = UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("fields", "country_currency_desc,exchange_rate,record_date")
                .queryParam("filter", filter)
                .queryParam("sort", "-record_date")
                .queryParam("page[size]", "1")
                .build()
                .toUriString();

        TreasuryApiResponse response = restClient.get()
                .uri(uri)
                .retrieve()
                .body(TreasuryApiResponse.class);

        if (response == null || response.data() == null || response.data().isEmpty()) {
            log.info("No exchange rate found for currency={} within {}-month window", currency, LOOKBACK_MONTHS);
            return Optional.empty();
        }

        TreasuryRateRecord record = response.data().get(0);
        ExchangeRateResult result = new ExchangeRateResult(
                record.currencyDesc(),
                record.exchangeRate(),
                record.recordDate());

        log.info("Found exchange rate: {} on {}", result.exchangeRate(), result.recordDate());
        return Optional.of(result);
    }

    @SuppressWarnings("unused") // invoked reflectively by Resilience4j
    private Optional<ExchangeRateResult> getExchangeRateFallback(String currency, LocalDate transactionDate, Exception e) {
        log.error("Circuit breaker fallback for currency={} transactionDate={}: {}", currency, transactionDate, e.getMessage());
        throw new TreasuryApiUnavailableException("Exchange rate service is temporarily unavailable", e);
    }

}
