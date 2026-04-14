package com.wex.purchasetransactions.service;

import static org.junit.jupiter.api.Assertions.*;

import com.wex.purchasetransactions.dto.PurchaseTransactionRequest;
import com.wex.purchasetransactions.model.PurchaseTransaction;
import com.wex.purchasetransactions.repository.PurchaseTransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PurchaseTransactionServiceTest {

    @Autowired
    private PurchaseTransactionService service;

    @Autowired
    private PurchaseTransactionRepository repository;

    @Test
    void store_shouldPersistTransactionAndReturnWithGeneratedId() {
        var request = new PurchaseTransactionRequest("Office supplies", LocalDate.of(2024, 1, 15), new BigDecimal("49.99"));

        PurchaseTransaction result = service.store(request);

        assertNotNull(result.getId());
        assertEquals("Office supplies", result.getDescription());
        assertEquals(LocalDate.of(2024, 1, 15), result.getTransactionDate());
        assertEquals(new BigDecimal("49.99"), result.getPurchaseAmount());

        // Verify it was actually persisted
        Optional<PurchaseTransaction> found = repository.findById(result.getId());
        assertTrue(found.isPresent());
        assertEquals(result.getId(), found.get().getId());
    }

    // Rounding behavior covered by StoreAmountRoundingProperties.
}
