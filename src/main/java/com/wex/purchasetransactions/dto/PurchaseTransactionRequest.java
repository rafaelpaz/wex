package com.wex.purchasetransactions.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PurchaseTransactionRequest(
        @NotBlank(message = "Description must not be blank")
                @Size(max = 50, message = "Description must not exceed 50 characters")
                String description,
        @NotNull(message = "Transaction date is required")
                @PastOrPresent(message = "Transaction date must not be in the future")
                @JsonFormat(pattern = "yyyy-MM-dd")
                LocalDate transactionDate,
        @NotNull(message = "Purchase amount is required")
                @Positive(message = "Purchase amount must be a positive value")
                BigDecimal purchaseAmount) {}
