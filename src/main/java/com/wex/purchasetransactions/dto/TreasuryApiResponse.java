package com.wex.purchasetransactions.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TreasuryApiResponse(List<TreasuryRateRecord> data) {

    public record TreasuryRateRecord(
            @JsonProperty("country_currency_desc") String currencyDesc,
            @JsonProperty("exchange_rate") BigDecimal exchangeRate,
            @JsonProperty("record_date") LocalDate recordDate) {}
}
