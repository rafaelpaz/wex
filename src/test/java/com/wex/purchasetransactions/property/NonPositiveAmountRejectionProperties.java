package com.wex.purchasetransactions.property;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.wex.purchasetransactions.repository.PurchaseTransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;

import net.jqwik.api.*;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

@JqwikSpringSupport
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class NonPositiveAmountRejectionProperties {

    @Autowired
    private PurchaseTransactionRepository repository;

    @LocalServerPort
    private int port;

    private RestClient restClient() {
        return RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    /**
     * Rejects zero or negative purchase amounts with HTTP 400 and verifies
     * no transaction is persisted.
     */
    @Property(tries = 100)
    void nonPositiveAmountIsRejectedWith400AndNoTransactionPersisted(
            @ForAll("nonPositiveAmounts") BigDecimal nonPositiveAmount,
            @ForAll("validDescriptions") String description,
            @ForAll("validDates") LocalDate transactionDate) {

        long countBefore = repository.count();

        String requestBody = """
                {
                  "description": "%s",
                  "transactionDate": "%s",
                  "purchaseAmount": %s
                }
                """
                .formatted(
                        description.replace("\"", "\\\""),
                        transactionDate.toString(),
                        nonPositiveAmount.toPlainString());

        ResponseEntity<String> response = restClient()
                .post()
                .uri("/api/v1/purchase-transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {/* don't throw */})
                .toEntity(String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), () -> "Expected 400 but got " + response.getStatusCode() + " for amount " + nonPositiveAmount.toPlainString());
        long countAfter = repository.count();
        assertEquals(countBefore, countAfter, () -> "Expected no new transactions but count changed from " + countBefore + " to " + countAfter);
    }

    @Provide
    Arbitrary<BigDecimal> nonPositiveAmounts() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("-99999.99"), BigDecimal.ZERO)
                .ofScale(2);
    }

    @Provide
    Arbitrary<String> validDescriptions() {
        return PropertyTestGenerators.alphaDescriptions();
    }

    @Provide
    Arbitrary<LocalDate> validDates() {
        return PropertyTestGenerators.validDates();
    }
}
