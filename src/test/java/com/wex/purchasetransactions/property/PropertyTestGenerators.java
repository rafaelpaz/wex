package com.wex.purchasetransactions.property;

import java.math.BigDecimal;
import java.time.LocalDate;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;

/**
 * Shared jqwik generators for property-based tests.
 */
final class PropertyTestGenerators {

    private PropertyTestGenerators() {
    }

    /**
     * Random LocalDate between 2000-01-01 and 2025-12-28.
     */
    static Arbitrary<LocalDate> dates(int startYear, int endYear) {
        return Arbitraries.integers()
                .between(startYear, endYear)
                .flatMap(year -> Arbitraries.integers().between(1, 12)
                        .map(month -> new int[]{year, month}))
                .flatMap(ym -> Arbitraries.integers().between(1, 28)
                        .map(day -> LocalDate.of(ym[0], ym[1], day)));
    }

    /**
     * Random LocalDate between 2000-01-01 and 2025-12-28.
     */
    static Arbitrary<LocalDate> validDates() {
        return dates(2000, 2025);
    }

    /**
     * Random LocalDate between 2020-01-01 and 2025-12-28.
     */
    static Arbitrary<LocalDate> transactionDates() {
        return dates(2020, 2025);
    }

    /**
     * Positive BigDecimal amounts with 2 decimal places, range [0.01, 99999.99].
     */
    static Arbitrary<BigDecimal> positiveAmounts() {
        return Arbitraries.bigDecimals().between(new BigDecimal("0.01"), new BigDecimal("99999.99")).ofScale(2);
    }

    /**
     * Alphanumeric strings between 1 and 50 characters.
     */
    static Arbitrary<String> validDescriptions() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(50);
    }

    /**
     * Alpha-only strings between 1 and 50 characters.
     */
    static Arbitrary<String> alphaDescriptions() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50);
    }
}
