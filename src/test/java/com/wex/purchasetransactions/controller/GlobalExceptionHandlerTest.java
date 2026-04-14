package com.wex.purchasetransactions.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.wex.purchasetransactions.dto.ErrorResponse;
import com.wex.purchasetransactions.exception.CurrencyConversionException;
import com.wex.purchasetransactions.exception.InvalidCurrencyException;
import com.wex.purchasetransactions.exception.TransactionNotFoundException;
import com.wex.purchasetransactions.exception.TreasuryApiUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleValidationErrors_returnsFieldMessages() {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "description", "Description must not exceed 50 characters"));
        bindingResult.addError(new FieldError("request", "purchaseAmount", "Purchase amount must be a positive value"));

        MethodParameter param;
        try {
            param = new MethodParameter(Object.class.getMethod("toString"), -1);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        var ex = new MethodArgumentNotValidException(param, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidationErrors(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Validation failed", response.getBody().error());
        assertEquals(2, response.getBody().details().size());
        assertTrue(response.getBody().details().contains("Description must not exceed 50 characters"));
        assertTrue(response.getBody().details().contains("Purchase amount must be a positive value"));
    }

    @Test
    void handleTransactionNotFound_returns404() {
        var ex = new TransactionNotFoundException("Transaction not found");

        ResponseEntity<ErrorResponse> response = handler.handleTransactionNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Transaction not found", response.getBody().error());
        assertNotNull(response.getBody().details());
        assertTrue(response.getBody().details().isEmpty());
    }

    @Test
    void handleCurrencyConversion_returns400() {
        var ex = new CurrencyConversionException("The purchase cannot be converted to the target currency");

        ResponseEntity<ErrorResponse> response = handler.handleCurrencyConversion(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("The purchase cannot be converted to the target currency", response.getBody().error());
    }

    @Test
    void handleTypeMismatch_returns400WithInvalidFormat() {
        var ex = new MethodArgumentTypeMismatchException("not-a-number", Long.class, "id", null, null);

        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid value for parameter 'id'", response.getBody().error());
    }

    @Test
    void handleTypeMismatch_returnsDateErrorForLocalDate() {
        var ex = new MethodArgumentTypeMismatchException("not-a-date", java.time.LocalDate.class, "startDate", null, null);

        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid date format for 'startDate'. Expected format: yyyy-MM-dd", response.getBody().error());
    }

    @Test
    void handleMessageNotReadable_returnsDateErrorForLocalDate() {
        var ex = new HttpMessageNotReadableException("Cannot deserialize value of type `java.time.LocalDate`", null, null);

        ResponseEntity<ErrorResponse> response = handler.handleMessageNotReadable(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Transaction date is required and must be a valid date", response.getBody().error());
    }

    @Test
    void handleMessageNotReadable_returnsMalformedForOtherErrors() {
        var ex = new HttpMessageNotReadableException("Unexpected character", null, null);

        ResponseEntity<ErrorResponse> response = handler.handleMessageNotReadable(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Malformed request body", response.getBody().error());
    }

    @Test
    void handleCircuitBreakerOpen_returns503() {
        var circuitBreaker = CircuitBreaker.ofDefaults("treasuryApi");
        var ex = CallNotPermittedException.createCallNotPermittedException(circuitBreaker);

        ResponseEntity<ErrorResponse> response = handler.handleCircuitBreakerOpen(ex);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Exchange rate service is temporarily unavailable", response.getBody().error());
    }

    @Test
    void handleInvalidCurrency_returns400() {
        var ex = new InvalidCurrencyException("Invalid currency format: Fake-Currency");

        ResponseEntity<ErrorResponse> response = handler.handleInvalidCurrency(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid currency format: Fake-Currency", response.getBody().error());
    }

    @Test
    void handleTreasuryApiUnavailable_returns503() {
        var ex = new TreasuryApiUnavailableException("Exchange rate service is temporarily unavailable", new RuntimeException("connection refused"));

        ResponseEntity<ErrorResponse> response = handler.handleTreasuryApiUnavailable(ex);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Exchange rate service is temporarily unavailable", response.getBody().error());
    }

    @Test
    void handleRuntimeException_returns500WithGenericMessage() {
        var ex = new RuntimeException("Something unexpected");

        ResponseEntity<ErrorResponse> response = handler.handleRuntimeException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("An unexpected error occurred", response.getBody().error());
    }
}
