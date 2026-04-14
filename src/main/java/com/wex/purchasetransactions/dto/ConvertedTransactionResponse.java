package com.wex.purchasetransactions.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ConvertedTransactionResponse(
        String id,
        String description,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate transactionDate,
        BigDecimal purchaseAmount,
        BigDecimal exchangeRate,
        String targetCurrency,
        BigDecimal convertedAmount) {}
