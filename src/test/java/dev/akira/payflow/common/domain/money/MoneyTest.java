package dev.akira.payflow.common.domain.money;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MoneyTest {

    private CurrencyCode brl;
    private CurrencyCode usd;

    @BeforeEach
    void setUp() {
        brl = CurrencyCode.of("BRL");
        usd = CurrencyCode.of("USD");
    }

    @Test
    void createsMoneyWithValidAmount() {
        Money money = Money.ofMinorUnits(1_000, brl);

        assertEquals(1_000, money.minorUnits());
        assertEquals(brl, money.currency());
    }

    @Test
    void rejectsNegativeAmount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Money.ofMinorUnits(-1, brl));
    }

    @Test
    void rejectsNullCurrency() {
        assertThrows(
                NullPointerException.class,
                () -> Money.ofMinorUnits(100, null));
    }

    @Test
    void addsAmountsWithSameCurrency() {
        Money result = Money.ofMinorUnits(1_000, brl)
                .add(Money.ofMinorUnits(500, brl));

        assertEquals(1_500, result.minorUnits());
        assertEquals(brl, result.currency());
    }

    @Test
    void rejectsAdditionWithDifferentCurrencies() {
        Money money = Money.ofMinorUnits(1_000, brl);
        Money other = Money.ofMinorUnits(500, usd);

        assertThrows(
                IllegalArgumentException.class,
                () -> money.add(other));
    }

    @Test
    void rejectsAdditionOverflow() {
        Money money = Money.ofMinorUnits(Long.MAX_VALUE, brl);
        Money other = Money.ofMinorUnits(1, brl);

        assertThrows(
                ArithmeticException.class,
                () -> money.add(other));
    }

    @Test
    void subtractsAmountsWithSameCurrency() {
        Money result = Money.ofMinorUnits(1_000, brl)
                .subtract(Money.ofMinorUnits(300, brl));

        assertEquals(700, result.minorUnits());
        assertEquals(brl, result.currency());
    }

    @Test
    void allowsSubtractionToZero() {
        Money result = Money.ofMinorUnits(500, brl)
                .subtract(Money.ofMinorUnits(500, brl));

        assertTrue(result.isZero());
    }

    @Test
    void rejectsSubtractionWhenAmountIsInsufficient() {
        Money money = Money.ofMinorUnits(100, brl);
        Money other = Money.ofMinorUnits(101, brl);

        assertThrows(
                IllegalArgumentException.class,
                () -> money.subtract(other));
    }

    @Test
    void rejectsSubtractionWithDifferentCurrencies() {
        Money money = Money.ofMinorUnits(1_000, brl);
        Money other = Money.ofMinorUnits(500, usd);

        assertThrows(
                IllegalArgumentException.class,
                () -> money.subtract(other));
    }

    @Test
    void rejectsSubtractionOfNullMoney() {
        Money money = Money.ofMinorUnits(1_000, brl);

        assertThrows(
                NullPointerException.class,
                () -> money.subtract(null));
    }

    @Test
    void multipliesAmountByNonNegativeMultiplier() {
        Money result = Money.ofMinorUnits(250, brl).multiply(4);

        assertEquals(1_000, result.minorUnits());
        assertEquals(brl, result.currency());
    }

    @Test
    void multiplyingByZeroProducesZero() {
        Money result = Money.ofMinorUnits(250, brl).multiply(0);

        assertTrue(result.isZero());
    }

    @Test
    void rejectsNegativeMultiplier() {
        Money money = Money.ofMinorUnits(250, brl);

        assertThrows(
                IllegalArgumentException.class,
                () -> money.multiply(-1));
    }

    @Test
    void rejectsMultiplicationOverflow() {
        Money money = Money.ofMinorUnits(Long.MAX_VALUE, brl);

        assertThrows(
                ArithmeticException.class,
                () -> money.multiply(2));
    }

    @Test
    void identifiesZeroAndPositiveAmounts() {
        assertTrue(Money.ofMinorUnits(0, brl).isZero());
        assertFalse(Money.ofMinorUnits(0, brl).isPositive());

        assertFalse(Money.ofMinorUnits(1, brl).isZero());
        assertTrue(Money.ofMinorUnits(1, brl).isPositive());
    }

    @Test
    void convertsMinorUnitsToMajorUnits() {
        Money money = Money.ofMinorUnits(1_234, brl);

        assertEquals(
                new BigDecimal("12.34"),
                money.toMajorUnits(2));
    }

    @Test
    void convertsMinorUnitsWithZeroDecimalPlaces() {
        Money money = Money.ofMinorUnits(123, brl);

        assertEquals(
                new BigDecimal("123"),
                money.toMajorUnits(0));
    }

    @Test
    void rejectsInvalidMinorUnitForConversion() {
        Money money = Money.ofMinorUnits(100, brl);

        assertThrows(
                IllegalArgumentException.class,
                () -> money.toMajorUnits(-1));

        assertThrows(
                IllegalArgumentException.class,
                () -> money.toMajorUnits(5));
    }

    @Test
    void createsMoneyFromMajorUnits() {
        Money money = Money.fromMajorUnits(
                new BigDecimal("12.34"),
                brl,
                2);

        assertEquals(1_234, money.minorUnits());
        assertEquals(brl, money.currency());
    }

    @Test
    void acceptsExtraTrailingZerosWithoutRounding() {
        Money money = Money.fromMajorUnits(
                new BigDecimal("12.340"),
                brl,
                2);

        assertEquals(1_234, money.minorUnits());
    }

    @Test
    void rejectsAmountRequiringRounding() {
        assertThrows(
                ArithmeticException.class,
                () -> Money.fromMajorUnits(
                        new BigDecimal("12.345"),
                        brl,
                        2));
    }

    @Test
    void rejectsNegativeMajorUnits() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Money.fromMajorUnits(
                        new BigDecimal("-1.00"),
                        brl,
                        2));
    }

    @Test
    void rejectsNullMajorAmount() {
        assertThrows(
                NullPointerException.class,
                () -> Money.fromMajorUnits(null, brl, 2));
    }

    @Test
    void rejectsNullCurrencyWhenCreatingFromMajorUnits() {
        assertThrows(
                NullPointerException.class,
                () -> Money.fromMajorUnits(
                        new BigDecimal("1.00"),
                        null,
                        2));
    }

    @Test
    void rejectsInvalidMinorUnitWhenCreatingFromMajorUnits() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Money.fromMajorUnits(
                        new BigDecimal("1.00"),
                        brl,
                        5));
    }

    @Test
    void rejectsMajorAmountOutsideLongRange() {
        assertThrows(
                ArithmeticException.class,
                () -> Money.fromMajorUnits(
                        new BigDecimal("92233720368547758.08"),
                        brl,
                        2));
    }
}
