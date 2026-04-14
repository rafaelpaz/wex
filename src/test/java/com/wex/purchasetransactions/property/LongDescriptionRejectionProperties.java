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
class LongDescriptionRejectionProperties {

    @Autowired
    private PurchaseTransactionRepository repository;

    @LocalServerPort
    private int port;

    private RestClient restClient() {
        return RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    /**
     * Descriptions longer than 50 characters are rejected with HTTP 400 and
     * no transaction is persisted.
     */
    @Property(tries = 100)
    void longDescriptionIsRejectedWith400AndNoTransactionPersisted(
            @ForAll("longDescriptions") String longDescription,
            @ForAll("validDates") LocalDate transactionDate,
            @ForAll("positiveAmounts") BigDecimal purchaseAmount) {

        long countBefore = repository.count();

        String requestBody = """
                {
                  "description": "%s",
                  "transactionDate": "%s",
                  "purchaseAmount": %s
                }
                """
                .formatted(
                        longDescription.replace("\"", "\\\""),
                        transactionDate.toString(),
                        purchaseAmount.toPlainString());

        ResponseEntity<String> response = restClient()
                .post()
                .uri("/api/v1/purchase-transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {/* don't throw */})
                .toEntity(String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), () -> "Expected 400 but got " + response.getStatusCode() + " for description of length " + longDescription.length());

        long countAfter = repository.count();
        assertEquals(countBefore, countAfter, () -> "Expected no new transactions but count changed from " + countBefore + " to " + countAfter);
    }

    @Provide
    Arbitrary<String> longDescriptions() {
        return Arbitraries.strings().alpha().ofMinLength(51).ofMaxLength(200);
    }

    @Provide
    Arbitrary<LocalDate> validDates() {
        return PropertyTestGenerators.validDates();
    }

    @Provide
    Arbitrary<BigDecimal> positiveAmounts() {
        return PropertyTestGenerators.positiveAmounts();
    }
}
