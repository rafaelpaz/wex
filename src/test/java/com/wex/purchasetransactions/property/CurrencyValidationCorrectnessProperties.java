package com.wex.purchasetransactions.property;

import static org.junit.jupiter.api.Assertions.*;

import com.wex.purchasetransactions.config.CurrencyValidationProperties;
import com.wex.purchasetransactions.exception.InvalidCurrencyException;
import com.wex.purchasetransactions.service.CurrencyValidator;

import java.util.Set;

import net.jqwik.api.*;

/**
 * Verifies that valid currencies pass and invalid currencies are rejected
 * when the validation feature flag is enabled.
 */
class CurrencyValidationCorrectnessProperties {

    private static final Set<String> VALID_CURRENCIES =
            Set.of(
                    "Canada-Dollar",
                    "Brazil-Real",
                    "Mexico-Peso",
                    "Japan-Yen",
                    "Euro Zone-Euro",
                    "United Kingdom-Pound",
                    "Australia-Dollar",
                    "India-Rupee",
                    "China-Renminbi",
                    "South Korea-Won",
                    "Switzerland-Franc",
                    "Sweden-Krona",
                    "Argentina-Peso",
                    "Colombia-Peso",
                    "Chile-Peso");

    private final CurrencyValidator validator;

    CurrencyValidationCorrectnessProperties() {
        CurrencyValidationProperties properties = new CurrencyValidationProperties(true, VALID_CURRENCIES);
        validator = new CurrencyValidator(properties);
    }

    /**
     * Currencies in the configured allow-list pass validation without exception.
     */
    @Property(tries = 100)
    void validCurrenciesPassValidation(@ForAll("validCurrencies") String currency) {
        assertDoesNotThrow(() -> validator.validate(currency), () -> "Valid currency '" + currency + "' should pass validation without exception");
    }

    /**
     * Currencies not in the allow-list are rejected with {@link InvalidCurrencyException}.
     */
    @Property(tries = 100)
    void invalidCurrenciesAreRejected(@ForAll("invalidCurrencies") String currency) {
        InvalidCurrencyException thrown = assertThrows(
                InvalidCurrencyException.class,
                () -> validator.validate(currency), () -> "Invalid currency '" + currency + "' should be rejected with InvalidCurrencyException");

        assertTrue(thrown.getMessage().contains(currency), () -> "Exception message should contain the invalid currency string: " + currency);
    }

    // --- Generators ---

    @Provide
    Arbitrary<String> validCurrencies() {
        return Arbitraries.of(VALID_CURRENCIES);
    }

    @Provide
    Arbitrary<String> invalidCurrencies() {
        return Arbitraries.oneOf(
                // Random alphabetic strings unlikely to match valid currencies
                Arbitraries.strings()
                        .alpha()
                        .ofMinLength(1)
                        .ofMaxLength(30)
                        .filter(s -> !VALID_CURRENCIES.contains(s)),
                // Strings with numbers and special characters
                Arbitraries.strings()
                        .ofMinLength(1)
                        .ofMaxLength(20)
                        .filter(s -> !VALID_CURRENCIES.contains(s)),
                // Known invalid patterns
                Arbitraries.of(
                        "USD",
                        "EUR",
                        "GBP",
                        "InvalidCurrency",
                        "canada-dollar",
                        "CANADA-DOLLAR",
                        "Canada Dollar",
                        "",
                        " ",
                        "123",
                        "!@#$"));
    }
}
