package com.wex.purchasetransactions.property;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.wex.purchasetransactions.client.TreasuryExchangeRateClient;
import com.wex.purchasetransactions.dto.ExchangeRateResult;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Supplier;

import net.jqwik.api.*;

/**
 * Verifies that an open circuit breaker rejects all requests immediately without making HTTP calls.
 */
class OpenCircuitBreakerFastFailProperties {

    private final CircuitBreaker circuitBreaker;
    private final TreasuryExchangeRateClient mockClient;

    OpenCircuitBreakerFastFailProperties() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(10)
                .build();
        circuitBreaker = CircuitBreaker.of("treasuryApi", config);
        circuitBreaker.transitionToOpenState();

        mockClient = mock(TreasuryExchangeRateClient.class);
    }

    /**
     * When the circuit breaker is OPEN, all requests are rejected immediately
     * with {@link CallNotPermittedException} and no HTTP call is made.
     */
    @Property(tries = 100)
    void openCircuitBreakerRejectsAllRequestsWithoutHttpCall(
            @ForAll("currencies") String currency,
            @ForAll("transactionDates") LocalDate transactionDate) {

        // Verify circuit is OPEN before each call
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState(), "Circuit breaker must be in OPEN state");

        // Wrap the mock client call with the circuit breaker
        Supplier<Optional<ExchangeRateResult>> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, () -> mockClient.getExchangeRate(currency, transactionDate));

        // The decorated call should throw CallNotPermittedException
        CallNotPermittedException thrown = assertThrows(CallNotPermittedException.class, decoratedSupplier::get, "Expected CallNotPermittedException when circuit breaker is OPEN");

        assertTrue(thrown.getMessage().contains("treasuryApi"), "Exception message should reference the circuit breaker name");

        // Verify no actual HTTP call was made to the Treasury API
        verify(mockClient, never()).getExchangeRate(anyString(), any(LocalDate.class));
    }

    // --- Generators ---

    @Provide
    Arbitrary<String> currencies() {
        return Arbitraries.of(
                "Canada-Dollar",
                "Brazil-Real",
                "Mexico-Peso",
                "Japan-Yen",
                "Euro Zone-Euro",
                "United Kingdom-Pound",
                "Australia-Dollar",
                "India-Rupee",
                "China-Renminbi",
                "South Korea-Won",
                "Switzerland-Franc",
                "Sweden-Krona");
    }

    @Provide
    Arbitrary<LocalDate> transactionDates() {
        return PropertyTestGenerators.transactionDates();
    }
}
