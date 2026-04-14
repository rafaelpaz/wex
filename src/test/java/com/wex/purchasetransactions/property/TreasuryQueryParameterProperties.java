package com.wex.purchasetransactions.property;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.wex.purchasetransactions.client.TreasuryExchangeRateClient;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.jqwik.api.*;

import org.springframework.web.client.RestClient;

/**
 * Verifies that the Treasury client constructs correct query parameters (fields, filter,
 * sort, page size) for arbitrary currencies and transaction dates.
 */
class TreasuryQueryParameterProperties implements AutoCloseable {

    private final WireMockServer wireMock;
    private final TreasuryExchangeRateClient client;

    TreasuryQueryParameterProperties() {
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
    void requestContainsCorrectQueryParameters(@ForAll("currencies") String currency, @ForAll("transactionDates") LocalDate transactionDate) {

        wireMock.resetAll();

        // Stub a valid response so the client completes the request
        stubFor(get(urlPathEqualTo("/")).willReturn(okJson("""
                {
                  "data": [
                    {
                      "country_currency_desc": "%s",
                      "exchange_rate": "1.00",
                      "record_date": "%s"
                    }
                  ]
                }
                """.formatted(currency, transactionDate.minusDays(1)))));

        client.getExchangeRate(currency, transactionDate);

        // Capture the request that was made
        List<LoggedRequest> requests = wireMock.findAll(getRequestedFor(urlPathEqualTo("/")));
        Assume.that(!requests.isEmpty());
        LoggedRequest request = requests.get(0);

        LocalDate windowStart = transactionDate.minusMonths(6);

        // Verify fields parameter
        String fields = request.queryParameter("fields").firstValue();
        assertEquals("country_currency_desc,exchange_rate,record_date", fields, "Expected fields=country_currency_desc,exchange_rate,record_date but got: " + fields);

        // Verify filter contains currency, start date, and end date
        String filter = request.queryParameter("filter").firstValue();
        assertTrue(filter.contains("country_currency_desc:eq:" + currency), "Filter should contain currency clause, got: " + filter);
        assertTrue(filter.contains("record_date:gte:" + windowStart), "Filter should contain start date (gte " + windowStart + "), got: " + filter);
        assertTrue(filter.contains("record_date:lte:" + transactionDate), "Filter should contain end date (lte " + transactionDate + "), got: " + filter);

        // Verify sort parameter
        String sort = request.queryParameter("sort").firstValue();
        assertEquals("-record_date", sort, "Expected sort=-record_date but got: " + sort);

        // Verify page size parameter
        String pageSize = request.queryParameter("page[size]").firstValue();
        assertEquals("1", pageSize, "Expected page[size]=1 but got: " + pageSize);
    }

    @Provide
    Arbitrary<String> currencies() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20).map(s -> s + "-Dollar");
    }

    @Provide
    Arbitrary<LocalDate> transactionDates() {
        return PropertyTestGenerators.transactionDates();
    }
}
