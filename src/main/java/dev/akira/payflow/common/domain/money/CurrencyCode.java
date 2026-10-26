package dev.akira.payflow.common.domain.money;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record CurrencyCode(String value) {
    
    private static final Pattern FORMAT = Pattern.compile("[A-Z]{3}");

    public CurrencyCode {
        Objects.requireNonNull(value, "Currency code must not be null");

        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException(
                "Currency code must contain exactly three uppercase letters"
            );
        }
    }

    public static CurrencyCode of(String value) {
        return new CurrencyCode(value);
    }

    public static CurrencyCode from(String value) {
        Objects.requireNonNull(value, "Currency code must not be null");
        return new CurrencyCode(value.toUpperCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return value;
    }
}
