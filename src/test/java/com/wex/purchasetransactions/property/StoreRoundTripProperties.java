package com.wex.purchasetransactions.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wex.purchasetransactions.dto.PurchaseTransactionRequest;
import com.wex.purchasetransactions.model.PurchaseTransaction;
import com.wex.purchasetransactions.repository.PurchaseTransactionRepository;
import com.wex.purchasetransactions.service.PurchaseTransactionService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;

import net.jqwik.api.*;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@JqwikSpringSupport
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StoreRoundTripProperties {

    @Autowired
    private PurchaseTransactionService service;

    @Autowired
    private PurchaseTransactionRepository repository;

    /**
     * Storing a valid transaction and retrieving it by ID yields the same description,
     * date, and amount (rounded to 2 decimal places).
     */
    @Property(tries = 100)
    void storeRoundTripPreservesTransactionData(
            @ForAll("validDescriptions") String description,
            @ForAll("validDates") LocalDate transactionDate,
            @ForAll("positiveAmounts") BigDecimal purchaseAmount) {

        PurchaseTransactionRequest request = new PurchaseTransactionRequest(description, transactionDate, purchaseAmount);

        PurchaseTransaction stored = service.store(request);

        Optional<PurchaseTransaction> retrieved = repository.findById(stored.getId());

        assertTrue(retrieved.isPresent(), "Transaction should be retrievable by returned ID");

        PurchaseTransaction tx = retrieved.get();
        BigDecimal expectedAmount = purchaseAmount.setScale(2, RoundingMode.HALF_UP);

        assertEquals(description, tx.getDescription(), () -> "Description mismatch");
        assertEquals(transactionDate, tx.getTransactionDate(), () -> "Date mismatch");
        assertEquals(0, expectedAmount.compareTo(tx.getPurchaseAmount()), () -> "Amount mismatch: expected " + expectedAmount + " but got " + tx.getPurchaseAmount());
    }

    @Provide
    Arbitrary<String> validDescriptions() {
        return PropertyTestGenerators.validDescriptions();
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
