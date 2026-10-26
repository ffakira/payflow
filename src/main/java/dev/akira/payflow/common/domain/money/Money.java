package dev.akira.payflow.common.domain.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Money(long minorUnits, CurrencyCode currency) {
    
    public Money {
        Objects.requireNonNull(currency, "Currency must not be null");

        if (minorUnits < 0) {
            throw new IllegalArgumentException(
                "Money amount must not be negative"
            );
        }
    }

    public static Money ofMinorUnits(long minorUnits, CurrencyCode currency) {
        return new Money(minorUnits, currency);
    }

    public Money add(Money other) {
        requireSameCurrency(other);

        return new Money(
            Math.addExact(minorUnits, other.minorUnits),
            currency
        );
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);

        if (other.minorUnits > minorUnits) {
            throw new IllegalArgumentException(
                "Insufficient amount for subtraction"
            );
        }

        return new Money(
            Math.subtractExact(minorUnits, other.minorUnits),
            currency
        );
    }

    public Money multiply(long multiplier) {
        if (multiplier < 0) {
            throw new IllegalArgumentException(
                "Multiplier must not be negative"
            );
        }

        return new Money(
            Math.multiplyExact(minorUnits, multiplier),
            currency
        );
    }

    public boolean isZero() {
        return minorUnits == 0;
    }

    public boolean isPositive() {
        return minorUnits > 0;
    }

    public BigDecimal toMajorUnits(int minorUnit) {
        validateMinorUnit(minorUnit);
        return BigDecimal.valueOf(minorUnits, minorUnit);
    }

    public static Money fromMajorUnits(BigDecimal amount, CurrencyCode currency, int minorUnit) {
        Objects.requireNonNull(amount, "Amount must not be null");
        Objects.requireNonNull(currency, "Currency must not be null");

        validateMinorUnit(minorUnit);

        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                "Money amount must not be negative"
            );
        }

        BigDecimal scaled = amount.setScale(
            minorUnit,
            RoundingMode.UNNECESSARY
        );

        return new Money(scaled.unscaledValue().longValueExact(), currency);
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "Money must not be null");

        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                "Currency mismatch: "
                    + currency
                    + " != "
                    + other.currency
            );
        }
    }

    private static void validateMinorUnit(int minorUnit) {
        if (minorUnit < 0 || minorUnit > 4) {
            throw new IllegalArgumentException(
                "Minor unit must be between 0 and 4"
            );
        }
    }
}
