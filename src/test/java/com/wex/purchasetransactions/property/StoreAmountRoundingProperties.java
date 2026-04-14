package com.wex.purchasetransactions.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wex.purchasetransactions.dto.PurchaseTransactionRequest;
import com.wex.purchasetransactions.model.PurchaseTransaction;
import com.wex.purchasetransactions.service.PurchaseTransactionService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import net.jqwik.api.*;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@JqwikSpringSupport
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StoreAmountRoundingProperties {

    @Autowired
    private PurchaseTransactionService service;

    /**
     * Amounts with more than two decimal places are stored rounded to two decimals
     * using HALF_UP.
     */
    @Property(tries = 100)
    void storedAmountMatchesHalfUpRoundingToTwoDecimalPlaces(@ForAll("positiveAmountsWithExtraDecimals") BigDecimal rawAmount) {

        PurchaseTransactionRequest request = new PurchaseTransactionRequest("Rounding test", LocalDate.of(2024, 6, 15), rawAmount);

        PurchaseTransaction stored = service.store(request);

        BigDecimal expected = rawAmount.setScale(2, RoundingMode.HALF_UP);

        assertEquals(0, stored.getPurchaseAmount().compareTo(expected), () -> "Expected " + expected + " but got " + stored.getPurchaseAmount() + " for raw amount " + rawAmount);
        assertTrue(stored.getPurchaseAmount().scale() <= 2, () -> "Expected scale <= 2 but got " + stored.getPurchaseAmount().scale());
    }

    @Provide
    Arbitrary<BigDecimal> positiveAmountsWithExtraDecimals() {
        return Arbitraries.integers()
                .between(3, 6)
                .flatMap(scale -> {
                    BigDecimal min = BigDecimal.ONE.movePointLeft(scale);
                    BigDecimal max = new BigDecimal("99999").setScale(scale, RoundingMode.HALF_UP);
                    return Arbitraries.bigDecimals().between(min, max).ofScale(scale);
                });
    }
}
