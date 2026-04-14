package com.wex.purchasetransactions.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.wex.purchasetransactions.dto.ConvertedTransactionResponse;
import com.wex.purchasetransactions.dto.PurchaseTransactionRequest;
import com.wex.purchasetransactions.exception.CurrencyConversionException;
import com.wex.purchasetransactions.exception.TransactionNotFoundException;
import com.wex.purchasetransactions.model.PurchaseTransaction;
import com.wex.purchasetransactions.service.PurchaseTransactionService;
import com.wex.purchasetransactions.model.PurchaseTransactionTestFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PurchaseTransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PurchaseTransactionService service;

    @Test
    void postValidTransaction_returns201WithCorrectResponseStructure() throws Exception {
        Long generatedId = 123456789L;
        PurchaseTransaction entity = PurchaseTransactionTestFactory.withId(generatedId, "Office supplies", LocalDate.of(2024, 1, 15), new BigDecimal("49.99"));

        when(service.store(any(PurchaseTransactionRequest.class))).thenReturn(entity);

        String requestBody = """
                {
                  "description": "Office supplies",
                  "transactionDate": "2024-01-15",
                  "purchaseAmount": 49.99
                }
                """;

        mockMvc.perform(post("/api/v1/purchase-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(generatedId))
                .andExpect(jsonPath("$.description").value("Office supplies"))
                .andExpect(jsonPath("$.transactionDate").value("2024-01-15"))
                .andExpect(jsonPath("$.purchaseAmount").value(49.99));
    }

    @Test
    void postWithInvalidDateFormat_returns400() throws Exception {
        String requestBody = """
                {
                  "description": "Test item",
                  "transactionDate": "not-a-date",
                  "purchaseAmount": 10.00
                }
                """;

        mockMvc.perform(post("/api/v1/purchase-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    void getWithNonExistentId_returns404() throws Exception {
        Long missingId = 999999L;

        when(service.retrieveWithConversion(eq(missingId), any())).thenThrow(new TransactionNotFoundException("Transaction not found"));

        mockMvc.perform(get("/api/v1/purchase-transactions/{id}", missingId).param("currency", "Canada-Dollar"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Transaction not found"));
    }

    @Test
    void getWithUnsupportedCurrency_returns400() throws Exception {
        Long id = 123456L;

        when(service.retrieveWithConversion(eq(id), eq("Nonexistent-Currency")))
                .thenThrow(new CurrencyConversionException("The purchase cannot be converted to the target currency"));

        mockMvc.perform(get("/api/v1/purchase-transactions/{id}", id).param("currency", "Nonexistent-Currency"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("The purchase cannot be converted to the target currency"));
    }

    @Test
    void getWithValidIdAndCurrency_returns200WithAllFields() throws Exception {
        Long id = 123456789L;

        ConvertedTransactionResponse converted = new ConvertedTransactionResponse(
                String.valueOf(id),
                "Office supplies",
                LocalDate.of(2024, 1, 15),
                new BigDecimal("49.99"),
                new BigDecimal("1.27"),
                "Canada-Dollar",
                new BigDecimal("63.49"));

        when(service.retrieveWithConversion(eq(id), eq("Canada-Dollar"))).thenReturn(converted);

        mockMvc.perform(get("/api/v1/purchase-transactions/{id}", id).param("currency", "Canada-Dollar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(String.valueOf(id)))
                .andExpect(jsonPath("$.description").value("Office supplies"))
                .andExpect(jsonPath("$.transactionDate").value("2024-01-15"))
                .andExpect(jsonPath("$.purchaseAmount").value(49.99))
                .andExpect(jsonPath("$.exchangeRate").value(1.27))
                .andExpect(jsonPath("$.targetCurrency").value("Canada-Dollar"))
                .andExpect(jsonPath("$.convertedAmount").value(63.49));
    }

    @Test
    void getWithNonNumericId_returns400WithTypeMismatchException() throws Exception {
        mockMvc.perform(get("/api/v1/purchase-transactions/abc").param("currency", "Canada-Dollar"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid value for parameter 'id'"))
                .andExpect(
                        result -> {
                            Exception resolved = result.getResolvedException();
                            assertNotNull(resolved);
                            assertInstanceOf(
                                    MethodArgumentTypeMismatchException.class,
                                    resolved,
                                    "Non-numeric ID should produce MethodArgumentTypeMismatchException");
                        });
    }

    @Test
    void postWithFutureDate_returns400WithValidationMessage() throws Exception {
        String futureDate = LocalDate.now().plusDays(5).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String requestBody = """
                {
                  "description": "Future purchase",
                  "transactionDate": "%s",
                  "purchaseAmount": 25.00
                }
                """.formatted(futureDate);

        mockMvc.perform(post("/api/v1/purchase-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value("Transaction date must not be in the future"));
    }
}
