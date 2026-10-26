
package dev.akira.payflow.common.domain.money;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CurrencyCodeTest {

    @Test
    void acceptsThreeUppercaseLetters() {
        CurrencyCode currency = CurrencyCode.of("BRL");

        assertEquals("BRL", currency.value());
    }

    @Test
    void rejectsNullCode() {
        assertThrows(
            NullPointerException.class,
            () -> CurrencyCode.of(null)
        );
    }

    @Test
    void rejectsLowercaseCodeWithStrictFactory() {
        assertThrows(
            IllegalArgumentException.class,
            () -> CurrencyCode.of("brl")
        );
    }

    @Test
    void rejectsCodeWithIncorrectLength() {
        assertThrows(
            IllegalArgumentException.class,
            () -> CurrencyCode.of("BR")
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> CurrencyCode.of("BRLX")
        );
    }

    @Test
    void rejectsDigitsAndSpecialCharacters() {
        assertThrows(
            IllegalArgumentException.class,
            () -> CurrencyCode.of("BR1")
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> CurrencyCode.of("B-R")
        );
    }

    @Test
    void normalisesLowercaseCode() {
        assertEquals(
            CurrencyCode.of("BRL"),
            CurrencyCode.from("brl")
        );
    }

    @Test
    void normalisesMixedCaseCode() {
        assertEquals(
            CurrencyCode.of("BRL"),
            CurrencyCode.from("BrL")
        );
    }

    @Test
    void rejectsNullInNormalisingFactory() {
        assertThrows(
            NullPointerException.class,
            () -> CurrencyCode.from(null)
        );
    }

    @Test
    void toStringReturnsCurrencyCode() {
        assertEquals("BRL", CurrencyCode.of("BRL").toString());
    }
}
