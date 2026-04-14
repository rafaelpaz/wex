package com.wex.purchasetransactions.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wex.purchasetransactions.dto.PurchaseTransactionRequest;
import com.wex.purchasetransactions.model.PurchaseTransaction;
import com.wex.purchasetransactions.service.PurchaseTransactionService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.jqwik.api.*;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@JqwikSpringSupport
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UniqueIdentifierProperties {

    @Autowired
    private PurchaseTransactionService service;

    /**
     * For any set of N stored purchase transactions, all N returned TSID identifiers
     * must be distinct positive Long values.
     */
    @Property(tries = 100)
    void allStoredTransactionsHaveDistinctPositiveIds(
            @ForAll("batchSizes") int n,
            @ForAll("validDescriptions") String description,
            @ForAll("validDates") LocalDate transactionDate,
            @ForAll("positiveAmounts") BigDecimal purchaseAmount) {

        List<Long> ids = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            PurchaseTransactionRequest request = new PurchaseTransactionRequest(description, transactionDate, purchaseAmount);
            PurchaseTransaction stored = service.store(request);
            ids.add(stored.getId());
        }

        Set<Long> uniqueIds = new HashSet<>(ids);
        assertEquals(n, uniqueIds.size(), () -> "Expected " + n + " distinct IDs but got " + uniqueIds.size());

        for (Long id : ids) {
            assertTrue(id > 0, () -> "TSID ID must be positive but was " + id);
        }
    }

    @Provide
    Arbitrary<Integer> batchSizes() {
        return Arbitraries.integers().between(2, 20);
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
