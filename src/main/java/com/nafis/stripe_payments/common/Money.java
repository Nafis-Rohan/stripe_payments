package com.nafis.stripe_payments.common;


import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;

/**
 * An amount of money, held as an integer count of the currency's minor unit:
 * 4999 = $49.99 USD, but 1000 = ¥1000 JPY, because JPY has no minor unit at all.
 * Entities still store a plain {@code long} + currency code. This type is for
 * doing arithmetic on them safely and rendering them for humans.
 */
public record Money(long amount, String currency) {

    public Money {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }
        currency = currency.toUpperCase(Locale.ROOT);
        Currency.getInstance(currency); // throws if not a valid ISO-4217 code
    }

    public static Money of(long amount, String currency) {
        return new Money(amount, currency);
    }

    public static Money zero(String currency) {
        return new Money(0L, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(amount, other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(amount, other.amount), currency);
    }

    public Money times(long quantity) {
        return new Money(Math.multiplyExact(amount, quantity), currency);
    }

    public boolean isZero()     { return amount == 0L; }
    public boolean isPositive() { return amount > 0L; }
    public boolean isNegative() { return amount < 0L; }

    /** Digits after the decimal point: 2 for USD, 0 for JPY, 3 for KWD. */
    public int fractionDigits() {
        int digits = Currency.getInstance(currency).getDefaultFractionDigits();
        return Math.max(digits, 0); // -1 for pseudo-currencies like XXX
    }

    /**
     * Human-readable form. This is the ONLY place in the codebase allowed to
     * shift the decimal point. Nothing else divides.
     */
    public String format() {
        return BigDecimal.valueOf(amount, fractionDigits()).toPlainString() + " " + currency;
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Cannot combine " + currency + " with " + other.currency);
        }
    }
}
