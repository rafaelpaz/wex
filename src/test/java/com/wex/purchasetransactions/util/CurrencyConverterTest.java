package com.wex.purchasetransactions.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CurrencyConverterTest {

    @Test
    void convertMultipliesAmountByRateAndRoundsToTwoDecimalPlaces() {
        BigDecimal amount = new BigDecimal("49.99");
        BigDecimal rate = new BigDecimal("1.27");
        BigDecimal expected = new BigDecimal("63.49");

        assertEquals(expected, CurrencyConverter.convert(amount, rate));
    }

    @Test
    void convertRoundsHalfUp() {
        // 10.00 * 1.005 = 10.050 → rounds to 10.05 (HALF_UP)
        BigDecimal amount = new BigDecimal("10.00");
        BigDecimal rate = new BigDecimal("1.005");

        assertEquals(new BigDecimal("10.05"), CurrencyConverter.convert(amount, rate));
    }

    @Test
    void convertWithWholeNumbers() {
        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal rate = new BigDecimal("2.00");

        assertEquals(new BigDecimal("200.00"), CurrencyConverter.convert(amount, rate));
    }

    @Test
    void convertWithSmallValues() {
        BigDecimal amount = new BigDecimal("0.01");
        BigDecimal rate = new BigDecimal("0.50");

        assertEquals(new BigDecimal("0.01"), CurrencyConverter.convert(amount, rate));
    }

    @Test
    void convertResultAlwaysHasTwoDecimalPlaces() {
        BigDecimal amount = new BigDecimal("5.00");
        BigDecimal rate = new BigDecimal("1.00");
        BigDecimal result = CurrencyConverter.convert(amount, rate);

        assertEquals(2, result.scale());
        assertEquals(new BigDecimal("5.00"), result);
    }
}
