package com.ledgerflow.common.money;

import java.util.Arrays;

public enum CurrencyCode {
    INR("Indian Rupee", 2, "₹"),
    USD("United States Dollar", 2, "$"),
    EUR("Euro", 2, "€"),
    GBP("British Pound", 2, "£");

    private final String displayName;
    private final int minorUnitExponent;
    private final String symbol;

    CurrencyCode(String displayName, int minorUnitExponent, String symbol) {
        this.displayName = displayName;
        this.minorUnitExponent = minorUnitExponent;
        this.symbol = symbol;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMinorUnitExponent() {
        return minorUnitExponent;
    }

    public String getSymbol() {
        return symbol;
    }

    public static boolean isValid(String code) {
        if (code == null) return false;
        return Arrays.stream(values())
                .anyMatch(c -> c.name().equalsIgnoreCase(code.trim()));
    }

    public static CurrencyCode fromString(String code) {
        if (code == null) {
            throw new IllegalArgumentException("Currency code cannot be null");
        }
        return Arrays.stream(values())
                .filter(c -> c.name().equalsIgnoreCase(code.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported currency: " + code + ". Supported: INR, USD, EUR, GBP"));
    }

    public String formatMinorUnits(long minorUnits) {
        double mainUnits = minorUnits / Math.pow(10, minorUnitExponent);
        return String.format("%s %.2f", symbol, mainUnits);
    }
}
