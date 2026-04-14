package com.wex.purchasetransactions.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.wex.purchasetransactions.client.TreasuryExchangeRateClient;
import com.wex.purchasetransactions.dto.ConvertedTransactionResponse;
import com.wex.purchasetransactions.dto.ExchangeRateResult;
import com.wex.purchasetransactions.exception.CurrencyConversionException;
import com.wex.purchasetransactions.exception.TransactionNotFoundException;
import com.wex.purchasetransactions.model.PurchaseTransaction;
import com.wex.purchasetransactions.repository.PurchaseTransactionRepository;
import com.wex.purchasetransactions.model.PurchaseTransactionTestFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RetrieveWithConversionServiceTest {

    @Mock
    private PurchaseTransactionRepository repository;

    @Mock
    private TreasuryExchangeRateClient exchangeRateClient;

    @Mock
    private CurrencyValidator currencyValidator;

    private PurchaseTransactionService service;

    @BeforeEach
    void setUp() {
        service = new PurchaseTransactionService(repository, exchangeRateClient, currencyValidator);
    }

    @Test
    void retrieveWithConversion_shouldReturnConvertedResponse() {
        Long id = 123456789L;
        LocalDate date = LocalDate.of(2024, 1, 15);
        PurchaseTransaction transaction = PurchaseTransactionTestFactory.withId(id, "Office supplies", date, new BigDecimal("49.99"));

        ExchangeRateResult rate = new ExchangeRateResult("Canada-Dollar", new BigDecimal("1.27"), LocalDate.of(2024, 1, 10));

        when(repository.findById(id)).thenReturn(Optional.of(transaction));
        when(exchangeRateClient.getExchangeRate("Canada-Dollar", date)).thenReturn(Optional.of(rate));

        ConvertedTransactionResponse response = service.retrieveWithConversion(id, "Canada-Dollar");

        assertEquals(String.valueOf(id), response.id());
        assertEquals("Office supplies", response.description());
        assertEquals(date, response.transactionDate());
        assertEquals(new BigDecimal("49.99"), response.purchaseAmount());
        assertEquals(new BigDecimal("1.27"), response.exchangeRate());
        assertEquals("Canada-Dollar", response.targetCurrency());
        assertEquals(new BigDecimal("63.49"), response.convertedAmount());
    }

    @Test
    void retrieveWithConversion_shouldThrowWhenTransactionNotFound() {
        Long id = 999999L;
        when(repository.findById(id)).thenReturn(Optional.empty());

        TransactionNotFoundException ex = assertThrows(TransactionNotFoundException.class, () -> service.retrieveWithConversion(id, "Canada-Dollar"));

        assertEquals("Transaction not found", ex.getMessage());
        verifyNoInteractions(exchangeRateClient);
    }

    @Test
    void retrieveWithConversion_shouldThrowWhenNoExchangeRateAvailable() {
        Long id = 123456789L;
        LocalDate date = LocalDate.of(2024, 1, 15);
        PurchaseTransaction transaction = PurchaseTransactionTestFactory.withId(
                id, "Test item", date, new BigDecimal("10.00"));

        when(repository.findById(id)).thenReturn(Optional.of(transaction));
        when(exchangeRateClient.getExchangeRate("Unknown-Currency", date)).thenReturn(Optional.empty());

        CurrencyConversionException ex = assertThrows(CurrencyConversionException.class, () -> service.retrieveWithConversion(id, "Unknown-Currency"));

        assertEquals("The purchase cannot be converted to the target currency", ex.getMessage());
    }
}
