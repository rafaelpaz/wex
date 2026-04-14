package com.wex.purchasetransactions.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ExchangeRateResult(String countryCurrencyDesc, BigDecimal exchangeRate, LocalDate recordDate)
        implements Serializable {}
