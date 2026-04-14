package com.wex.purchasetransactions.property;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.wex.purchasetransactions.client.TreasuryExchangeRateClient;
import com.wex.purchasetransactions.dto.ExchangeRateResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import net.jqwik.api.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class ExchangeRateSelectionProperties implements AutoCloseable {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String CURRENCY = "Canada-Dollar";

    private final WireMockServer wireMock;
    private final TreasuryExchangeRateClient client;

    ExchangeRateSelectionProperties() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(5));
        factory.setReadTimeout(java.time.Duration.ofSeconds(10));
        RestClient restClient = RestClient.builder().requestFactory(factory).build();
        client = new TreasuryExchangeRateClient(restClient, wireMock.baseUrl());
    }

    @Override
    public void close() {
        if (wireMock != null && wireMock.isRunning()) {
            wireMock.stop();
        }
    }

    /**
     * The selected exchange rate has a record_date within the 6-month window before the
     * transaction date and is the most recent such record.
     */
    @Property(tries = 100)
    void selectedRateIsTheMostRecentWithinSixMonthWindow(
            @ForAll("transactionDates") LocalDate transactionDate,
            @ForAll("rateRecordSets") List<RateRecord> allRecords) {

        wireMock.resetAll();

        // Determine the valid window
        LocalDate windowStart = transactionDate.minusMonths(6);

        // Find records within the valid window: record_date >= windowStart AND record_date <=
        // transactionDate
        List<RateRecord> validRecords = allRecords.stream().filter(
                        r -> !r.recordDate().isBefore(windowStart) && !r.recordDate().isAfter(transactionDate))
                .sorted(Comparator.comparing(RateRecord::recordDate).reversed())
                .toList();

        if (validRecords.isEmpty()) {
            // No valid records — mock empty response
            wireMock.stubFor(get(urlPathEqualTo("/")).willReturn(okJson("{\"data\":[]}")));

            Optional<ExchangeRateResult> result = client.getExchangeRate(CURRENCY, transactionDate);
            assertTrue(result.isEmpty(), () -> "Expected empty result when no valid rates exist, but got " + result.get());
            return;
        }

        // The expected best record is the most recent valid one
        RateRecord expected = validRecords.getFirst();

        // Mock WireMock to return the expected record (simulating server-side filter + sort)
        String responseJson =
                """
                        {
                          "data": [
                            {
                              "country_currency_desc": "%s",
                              "exchange_rate": "%s",
                              "record_date": "%s"
                            }
                          ]
                        }
                        """
                        .formatted(
                                CURRENCY,
                                expected.exchangeRate().toPlainString(),
                                expected.recordDate().format(DATE_FMT));

        wireMock.stubFor(get(urlPathEqualTo("/")).willReturn(okJson(responseJson)));

        Optional<ExchangeRateResult> result = client.getExchangeRate(CURRENCY, transactionDate);

        // Assert a result was returned
        assertTrue(result.isPresent(), "Expected a rate to be selected but got empty");

        ExchangeRateResult selected = result.get();

        // Assert record_date <= transactionDate
        assertFalse(selected.recordDate().isAfter(transactionDate), () -> "Selected rate record_date " + selected.recordDate() + " is after transaction date " + transactionDate);

        // Assert record_date >= windowStart (within 6 months)
        assertFalse(selected.recordDate().isBefore(windowStart), () -> "Selected rate record_date " + selected.recordDate() + " is before 6-month window start " + windowStart);

        // Assert it is the most recent valid record
        assertEquals(expected.recordDate(), selected.recordDate(), () -> "Expected most recent record_date " + expected.recordDate() + " but got " + selected.recordDate());

        assertEquals(0, selected.exchangeRate().compareTo(expected.exchangeRate()), () -> "Expected exchange_rate " + expected.exchangeRate() + " but got " + selected.exchangeRate());

        // Verify the client sent correct query parameters
        wireMock.verify(getRequestedFor(urlPathEqualTo("/"))
                .withQueryParam("filter", containing("country_currency_desc:eq:" + CURRENCY))
                .withQueryParam("filter", containing("record_date:lte:" + transactionDate.format(DATE_FMT)))
                .withQueryParam("filter", containing("record_date:gte:" + windowStart.format(DATE_FMT)))
                .withQueryParam("sort", equalTo("-record_date"))
                .withQueryParam("page[size]", equalTo("1")));
    }

    // --- Generators ---

    @Provide
    Arbitrary<LocalDate> transactionDates() {
        return PropertyTestGenerators.transactionDates();
    }

    @Provide
    Arbitrary<List<RateRecord>> rateRecordSets() {
        Arbitrary<RateRecord> singleRecord = PropertyTestGenerators.dates(2019, 2026)
                .flatMap(date ->
                        Arbitraries.bigDecimals().between(new BigDecimal("0.01"), new BigDecimal("999.9999"))
                                .ofScale(4)
                                .map(rate -> new RateRecord(date, rate)));

        return singleRecord.list().ofMinSize(1).ofMaxSize(10);
    }

    record RateRecord(LocalDate recordDate, BigDecimal exchangeRate) {
    }
}
