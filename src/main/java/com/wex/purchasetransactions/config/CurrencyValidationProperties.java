package com.wex.purchasetransactions.config;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "features.currency-validation")
public record CurrencyValidationProperties(boolean enabled, Set<String> validCurrencies) {
    public CurrencyValidationProperties {
        if (validCurrencies == null) {
            validCurrencies = Set.of();
        }
    }
}
