package com.wex.purchasetransactions.property;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.wex.purchasetransactions.util.CurrencyConverter;

import java.math.BigDecimal;
import java.math.RoundingMode;

import net.jqwik.api.*;

class CurrencyConversionProperties {

    /**
     * Converted amount equals {@code amount × rate} rounded to two decimal places using HALF_UP.
     */
    @Property(tries = 200)
    void convertedAmountEqualsAmountTimesRateRoundedHalfUp(
            @ForAll("positiveAmounts") BigDecimal amount,
            @ForAll("positiveRates") BigDecimal rate) {

        BigDecimal result = CurrencyConverter.convert(amount, rate);
        BigDecimal expected = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);

        assertEquals(0, result.compareTo(expected), () -> "Expected " + expected + " but got " + result + " for amount=" + amount + ", rate=" + rate);
        assertEquals(2, result.scale(), () -> "Expected scale 2 but got " + result.scale());
    }

    @Provide
    Arbitrary<BigDecimal> positiveAmounts() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("0.01"), new BigDecimal("999999999.99"))
                .ofScale(2);
    }

    @Provide
    Arbitrary<BigDecimal> positiveRates() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("0.0001"), new BigDecimal("99999.9999"))
                .ofScale(4);
    }
}
