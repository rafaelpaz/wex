package com.wex.purchasetransactions.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Test factory for creating {@link PurchaseTransaction} instances with a pre-set ID.
 * <p>
 * Located in the same package as the entity so it can use the package-private
 * constructor directly — no reflection required.
 */
public final class PurchaseTransactionTestFactory {

    private PurchaseTransactionTestFactory() {}

    public static PurchaseTransaction withId(Long id, String description, LocalDate transactionDate, BigDecimal purchaseAmount) {
        return new PurchaseTransaction(id, description, transactionDate, purchaseAmount);
    }
}
