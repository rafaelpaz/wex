package com.wex.purchasetransactions.property;

import static org.junit.jupiter.api.Assertions.*;

import com.wex.purchasetransactions.config.CurrencyValidationProperties;
import com.wex.purchasetransactions.service.CurrencyValidator;

import java.util.Set;

import net.jqwik.api.*;

/**
 * Verifies that all currency strings pass validation when the feature flag is disabled.
 */
class DisabledCurrencyValidationBypassProperties {

    private final CurrencyValidator validator;

    DisabledCurrencyValidationBypassProperties() {
        CurrencyValidationProperties properties = new CurrencyValidationProperties(
                false,
                Set.of(
                        "Canada-Dollar",
                        "Brazil-Real",
                        "Mexico-Peso",
                        "Japan-Yen",
                        "Euro Zone-Euro",
                        "United Kingdom-Pound"));
        validator = new CurrencyValidator(properties);
    }

    /**
     * With validation disabled, any currency string is accepted without exception.
     */
    @Property(tries = 100)
    void disabledValidationNeverRejects(@ForAll("anyCurrencyString") String currency) {
        assertDoesNotThrow(() -> validator.validate(currency), () -> "With validation disabled, currency '" + currency + "' should pass without exception");
    }

    // --- Generators ---

    @Provide
    Arbitrary<String> anyCurrencyString() {
        return Arbitraries.oneOf(
                // Completely random strings (including special chars, unicode)
                Arbitraries.strings().ofMinLength(0).ofMaxLength(50),
                // Empty and whitespace strings
                Arbitraries.of("", " ", "  ", "\t", "\n"),
                // Known invalid currency formats
                Arbitraries.of("USD", "EUR", "INVALID", "123", "!@#$%", "null", "undefined"),
                // Strings that look like valid currencies but aren't
                Arbitraries.of("canada-dollar", "CANADA-DOLLAR", "Canada Dollar", "Fake-Currency"),
                // Actual valid currencies (should also pass when disabled)
                Arbitraries.of("Canada-Dollar", "Brazil-Real", "Mexico-Peso", "Japan-Yen"));
    }
}
