package com.wex.purchasetransactions.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class CurrencyConverter {

    private CurrencyConverter() {}

    public static BigDecimal convert(BigDecimal amount, BigDecimal exchangeRate) {
        return amount.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
    }
}
