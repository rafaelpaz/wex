package com.wex.purchasetransactions.property;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.wex.purchasetransactions.client.TreasuryExchangeRateClient;
import com.wex.purchasetransactions.dto.ExchangeRateResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import net.jqwik.api.*;

import org.springframework.web.client.RestClient;

/**
 * Verifies that the Treasury client correctly parses country_currency_desc, exchange_rate,
 * and record_date from arbitrary valid API responses.
 */
class TreasuryResponseParsingProperties implements AutoCloseable {

    private final WireMockServer wireMock;
    private final TreasuryExchangeRateClient client;

    TreasuryResponseParsingProperties() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        configureFor("localhost", wireMock.port());
        client = new TreasuryExchangeRateClient(
                RestClient.builder().build(),
                wireMock.baseUrl());
    }

    @Override
    public void close() {
        wireMock.stop();
    }

    @Property(tries = 100)
    void parsedFieldsMatchGeneratedValues(
            @ForAll("currencies") String currency,
            @ForAll("exchangeRates") BigDecimal exchangeRate,
            @ForAll("recordDates") LocalDate recordDate) {

        wireMock.resetAll();

        String rateStr = exchangeRate.toPlainString();
        String dateStr = recordDate.toString();

        String jsonBody = """
                {
                  "data": [
                    {
                      "country_currency_desc": "%s",
                      "exchange_rate": "%s",
                      "record_date": "%s"
                    }
                  ]
                }
                """.formatted(currency, rateStr, dateStr);

        stubFor(get(urlPathEqualTo("/")).willReturn(okJson(jsonBody)));

        // Use a transaction date after the record date so it falls within the 6-month window
        LocalDate transactionDate = recordDate.plusDays(1);
        Optional<ExchangeRateResult> result = client.getExchangeRate(currency, transactionDate);

        assertTrue(result.isPresent(), "Expected a parsed exchange rate result");

        ExchangeRateResult rate = result.get();
        assertEquals(currency, rate.countryCurrencyDesc(),
                "country_currency_desc should be preserved");
        assertEquals(0, exchangeRate.compareTo(rate.exchangeRate()),
                () -> "exchange_rate should be preserved: expected "
                        + exchangeRate + " but got " + rate.exchangeRate());
        assertEquals(recordDate, rate.recordDate(),
                "record_date should be preserved");
    }

    @Provide
    Arbitrary<String> currencies() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20)
                .map(s -> s + "-Dollar");
    }

    @Provide
    Arbitrary<BigDecimal> exchangeRates() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("0.0001"), new BigDecimal("99999.9999"))
                .ofScale(4);
    }

    @Provide
    Arbitrary<LocalDate> recordDates() {
        return PropertyTestGenerators.validDates();
    }
}
