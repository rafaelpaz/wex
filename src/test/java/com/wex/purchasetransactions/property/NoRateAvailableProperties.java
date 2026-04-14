package com.wex.purchasetransactions.property;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.wex.purchasetransactions.client.TreasuryExchangeRateClient;
import com.wex.purchasetransactions.dto.ExchangeRateResult;
import com.wex.purchasetransactions.exception.CurrencyConversionException;
import com.wex.purchasetransactions.model.PurchaseTransaction;
import com.wex.purchasetransactions.repository.PurchaseTransactionRepository;
import com.wex.purchasetransactions.service.CurrencyValidator;
import com.wex.purchasetransactions.service.PurchaseTransactionService;
import com.wex.purchasetransactions.model.PurchaseTransactionTestFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import net.jqwik.api.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class NoRateAvailableProperties implements AutoCloseable {

    private final WireMockServer wireMock;
    private final TreasuryExchangeRateClient client;

    NoRateAvailableProperties() {
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
     * When the Treasury API returns an empty data array, getExchangeRate returns Optional.empty().
     */
    @Property(tries = 100)
    void emptyTreasuryResponseReturnsEmptyOptional(
            @ForAll("transactionDates") LocalDate transactionDate,
            @ForAll("currencies") String currency) {

        wireMock.resetAll();
        wireMock.stubFor(get(urlPathEqualTo("/")).willReturn(okJson("{\"data\":[]}")));

        Optional<ExchangeRateResult> result = client.getExchangeRate(currency, transactionDate);

        assertTrue(result.isEmpty(), () -> "Expected Optional.empty() when Treasury API returns no rates, but got " + result.get());
    }

    /**
     * When no exchange rate is available, retrieveWithConversion throws CurrencyConversionException.
     */
    @Property(tries = 100)
    void noRateAvailableThrowsCurrencyConversionException(
            @ForAll("transactionDates") LocalDate transactionDate,
            @ForAll("currencies") String currency,
            @ForAll("descriptions") String description,
            @ForAll("amounts") BigDecimal amount) {

        wireMock.resetAll();
        wireMock.stubFor(get(urlPathEqualTo("/")).willReturn(okJson("{\"data\":[]}")));

        // Create a mock repository that returns a transaction
        PurchaseTransactionRepository mockRepo = mock(PurchaseTransactionRepository.class);
        Long txId = 123456789L;
        PurchaseTransaction tx = PurchaseTransactionTestFactory.withId(txId, description, transactionDate, amount);
        when(mockRepo.findById(txId)).thenReturn(Optional.of(tx));

        PurchaseTransactionService service = new PurchaseTransactionService(mockRepo, client, mock(CurrencyValidator.class));

        CurrencyConversionException e = assertThrows(CurrencyConversionException.class, () -> service.retrieveWithConversion(txId, currency));
        assertTrue(e.getMessage().contains("cannot be converted"), () -> "Expected message about conversion failure, got: " + e.getMessage());
    }

    // --- Generators ---

    @Provide
    Arbitrary<LocalDate> transactionDates() {
        return PropertyTestGenerators.transactionDates();
    }

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
                "China-Renminbi");
    }

    @Provide
    Arbitrary<String> descriptions() {
        return PropertyTestGenerators.alphaDescriptions();
    }

    @Provide
    Arbitrary<BigDecimal> amounts() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("0.01"), new BigDecimal("9999.99"))
                .ofScale(2);
    }
}
