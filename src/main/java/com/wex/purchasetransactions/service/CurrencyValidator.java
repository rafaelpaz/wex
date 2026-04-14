package com.wex.purchasetransactions.service;

import com.wex.purchasetransactions.config.CurrencyValidationProperties;
import com.wex.purchasetransactions.exception.InvalidCurrencyException;
import org.springframework.stereotype.Component;

@Component
public class CurrencyValidator {

    private final CurrencyValidationProperties properties;

    public CurrencyValidator(CurrencyValidationProperties properties) {
        this.properties = properties;
    }

    public void validate(String currency) {
        if (!properties.enabled()) {
            return;
        }
        if (!properties.validCurrencies().contains(currency)) {
            throw new InvalidCurrencyException("Invalid currency format: " + currency);
        }
    }
}
