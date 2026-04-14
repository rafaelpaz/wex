package com.wex.purchasetransactions.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.wex.purchasetransactions.dto.ConvertedTransactionResponse;
import com.wex.purchasetransactions.dto.ErrorResponse;
import com.wex.purchasetransactions.dto.PaginatedTransactionResponse;
import com.wex.purchasetransactions.dto.PurchaseTransactionRequest;
import com.wex.purchasetransactions.dto.PurchaseTransactionResponse;
import com.wex.purchasetransactions.repository.PurchaseTransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PurchaseTransactionIntegrationTest {

    static WireMockServer wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

    @LocalServerPort
    int port;

    @Autowired
    CacheManager cacheManager;

    @Autowired
    CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    PurchaseTransactionRepository repository;

    RestClient client;

    @BeforeAll
    static void startWireMock() {
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @DynamicPropertySource
    static void overrideTreasuryUrl(DynamicPropertyRegistry registry) {
        registry.add("treasury.api.base-url", () -> wireMock.baseUrl());
    }

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        wireMock.resetAll();
        cacheManager.getCacheNames().forEach(
                name -> {
                    var cache = cacheManager.getCache(name);
                    if (cache != null) {
                        cache.clear();
                    }
                });
        circuitBreakerRegistry.circuitBreaker("treasuryApi").reset();
    }

    @Test
    void storeAndRetrieveWithConversion() {
        // 1. Store a transaction
        var request = new PurchaseTransactionRequest("Integration test item", LocalDate.of(2024, 3, 15), new BigDecimal("100.00"));

        PurchaseTransactionResponse stored =
                client.post()
                        .uri("/api/v1/purchase-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(PurchaseTransactionResponse.class);

        assertNotNull(stored);
        assertNotNull(stored.id());
        assertEquals("Integration test item", stored.description());
        assertEquals(new BigDecimal("100.00"), stored.purchaseAmount());

        // 2. Stub WireMock to return an exchange rate
        String treasuryResponse = """
                {
                  "data": [
                    {
                      "country_currency_desc": "Canada-Dollar",
                      "exchange_rate": "1.35",
                      "record_date": "2024-03-10"
                    }
                  ]
                }
                """;
        wireMock.stubFor(get(urlPathEqualTo("/")).willReturn(okJson(treasuryResponse)));

        // 3. Retrieve with conversion
        ConvertedTransactionResponse converted = client.get()
                .uri("/api/v1/purchase-transactions/{id}?currency={currency}", stored.id(), "Canada-Dollar")
                .retrieve()
                .body(ConvertedTransactionResponse.class);

        assertNotNull(converted);
        assertEquals(stored.id(), converted.id());
        assertEquals("Integration test item", converted.description());
        assertEquals(new BigDecimal("100.00"), converted.purchaseAmount());
        assertEquals(new BigDecimal("1.35"), converted.exchangeRate());
        assertEquals("Canada-Dollar", converted.targetCurrency());
        // 100.00 * 1.35 = 135.00
        assertEquals(new BigDecimal("135.00"), converted.convertedAmount());
    }

    @Test
    void treasuryApiErrorReturns503() {
        // Store a transaction first
        var request = new PurchaseTransactionRequest("Error test item", LocalDate.of(2024, 6, 1), new BigDecimal("50.00"));

        PurchaseTransactionResponse stored = client.post()
                .uri("/api/v1/purchase-transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(PurchaseTransactionResponse.class);

        assertNotNull(stored);

        // Stub WireMock to return 500
        wireMock.stubFor(get(urlPathEqualTo("/")).willReturn(serverError().withBody("Internal Server Error")));

        // Retrieve with conversion — should get 503
        var responseSpec = client.get()
                .uri("/api/v1/purchase-transactions/{id}?currency={currency}", stored.id(), "Brazil-Real")
                .exchange(
                        (req, res) -> {
                            assertEquals(503, res.getStatusCode().value(), "Expected 503 Service Unavailable when Treasury API returns error");
                            return res.bodyTo(ErrorResponse.class);
                        });

        assertNotNull(responseSpec);
        assertEquals("Exchange rate service is temporarily unavailable", responseSpec.error());
    }

    @Test
    void applicationContextLoads() {
        String health = client.get()
                .uri("/actuator/health")
                .exchange((req, res) -> res.bodyTo(String.class));

        assertNotNull(health);
        assertTrue(health.contains("status"));
    }

    @Test
    void listTransactionsDefaultPagination() {
        repository.deleteAll();

        storeTransaction("Alpha item", LocalDate.of(2024, 1, 10), "10.00");
        storeTransaction("Beta item", LocalDate.of(2024, 2, 15), "20.00");
        storeTransaction("Gamma item", LocalDate.of(2024, 3, 20), "30.00");
        storeTransaction("Delta item", LocalDate.of(2024, 4, 25), "40.00");

        PaginatedTransactionResponse page = client.get()
                .uri("/api/v1/purchase-transactions")
                .retrieve()
                .body(PaginatedTransactionResponse.class);

        assertNotNull(page);
        assertEquals(4, page.totalElements());
        assertEquals(1, page.totalPages());
        assertEquals(0, page.pageNumber());
        assertEquals(20, page.pageSize());
        assertEquals(4, page.content().size());
    }

    @Test
    void listTransactionsWithPaginationParams() {
        repository.deleteAll();

        storeTransaction("Page item A", LocalDate.of(2024, 1, 1), "10.00");
        storeTransaction("Page item B", LocalDate.of(2024, 2, 1), "20.00");
        storeTransaction("Page item C", LocalDate.of(2024, 3, 1), "30.00");
        storeTransaction("Page item D", LocalDate.of(2024, 4, 1), "40.00");

        PaginatedTransactionResponse page = client.get()
                .uri("/api/v1/purchase-transactions?page=0&size=2")
                .retrieve()
                .body(PaginatedTransactionResponse.class);

        assertNotNull(page);
        assertEquals(2, page.content().size());
        assertEquals(4, page.totalElements());
        assertEquals(2, page.totalPages());
        assertEquals(0, page.pageNumber());
        assertEquals(2, page.pageSize());
    }

    @Test
    void listTransactionsFilterByDescription() {
        repository.deleteAll();

        storeTransaction("Unique gadget purchase", LocalDate.of(2024, 1, 5), "15.00");
        storeTransaction("Office supplies", LocalDate.of(2024, 2, 10), "25.00");
        storeTransaction("Another gadget order", LocalDate.of(2024, 3, 15), "35.00");

        PaginatedTransactionResponse page = client.get()
                .uri("/api/v1/purchase-transactions?description=gadget")
                .retrieve()
                .body(PaginatedTransactionResponse.class);

        assertNotNull(page);
        assertEquals(2, page.totalElements());
        assertEquals(2, page.content().size());
        page.content().forEach(tx ->
                assertTrue(tx.description().toLowerCase().contains("gadget"),
                        "Expected description to contain 'gadget' but was: " + tx.description()));
    }

    @Test
    void listTransactionsFilterByDateRange() {
        repository.deleteAll();

        storeTransaction("Jan item", LocalDate.of(2024, 1, 15), "10.00");
        storeTransaction("Mar item", LocalDate.of(2024, 3, 15), "20.00");
        storeTransaction("Jun item", LocalDate.of(2024, 6, 15), "30.00");
        storeTransaction("Sep item", LocalDate.of(2024, 9, 15), "40.00");

        PaginatedTransactionResponse page = client.get()
                .uri("/api/v1/purchase-transactions?startDate=2024-02-01&endDate=2024-07-01")
                .retrieve()
                .body(PaginatedTransactionResponse.class);

        assertNotNull(page);
        assertEquals(2, page.totalElements());
        assertEquals(2, page.content().size());
        page.content().forEach(tx -> {
            assertTrue(!tx.transactionDate().isBefore(LocalDate.of(2024, 2, 1)),
                    "Transaction date should be on or after 2024-02-01 but was: " + tx.transactionDate());
            assertTrue(!tx.transactionDate().isAfter(LocalDate.of(2024, 7, 1)),
                    "Transaction date should be on or before 2024-07-01 but was: " + tx.transactionDate());
        });
    }

    @Test
    void persistedTransactionHasTsidGeneratedId() {
        PurchaseTransactionResponse stored = storeTransaction(
                "TSID wiring check", LocalDate.of(2024, 5, 1), "99.99");

        assertNotNull(stored.id(), "Persisted transaction ID must not be null");

        long id = Long.parseLong(stored.id());
        assertTrue(id > 0, "TSID-generated ID must be positive, but was: " + id);
    }

    private PurchaseTransactionResponse storeTransaction(String description, LocalDate date, String amount) {
        var request = new PurchaseTransactionRequest(description, date, new BigDecimal(amount));
        return client.post()
                .uri("/api/v1/purchase-transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(PurchaseTransactionResponse.class);
    }
}
