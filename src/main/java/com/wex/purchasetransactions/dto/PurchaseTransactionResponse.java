package com.wex.purchasetransactions.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.wex.purchasetransactions.model.PurchaseTransaction;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PurchaseTransactionResponse(
        String id,
        String description,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate transactionDate,
        BigDecimal purchaseAmount) {

    public static PurchaseTransactionResponse from(PurchaseTransaction entity) {
        return new PurchaseTransactionResponse(
                String.valueOf(entity.getId()),
                entity.getDescription(),
                entity.getTransactionDate(),
                entity.getPurchaseAmount());
    }
}
