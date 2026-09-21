package com.bj.marketdata.value;

public final class ScaledPrice {
    public static final int SCALE = 4;
    public static final long SCALE_FACTOR = 10_000L;

    private ScaledPrice() {
    }

    public static long parse(final String text) {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        final String value = text.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("text must not be blank");
        }

        int index = 0;
        boolean negative = false;
        final char firstChar = value.charAt(0);
        if (firstChar == '+' || firstChar == '-') {
            negative = firstChar == '-';
            index++;
        }
        if (index >= value.length()) {
            throw new IllegalArgumentException("invalid numeric value: " + text);
        }

        long wholePart = 0L;
        long fractionalPart = 0L;
        int fractionalDigits = 0;
        boolean dotSeen = false;
        boolean digitSeen = false;
        for (; index < value.length(); index++) {
            final char ch = value.charAt(index);
            if (ch == '.') {
                if (dotSeen) {
                    throw new IllegalArgumentException("invalid numeric value: " + text);
                }
                dotSeen = true;
                continue;
            }
            if (ch < '0' || ch > '9') {
                throw new IllegalArgumentException("invalid numeric value: " + text);
            }
            digitSeen = true;
            final int digit = ch - '0';
            if (!dotSeen) {
                wholePart = Math.addExact(Math.multiplyExact(wholePart, 10L), digit);
                continue;
            }
            if (fractionalDigits >= SCALE) {
                if (digit != 0) {
                    throw new IllegalArgumentException("too many fractional digits for scale " + SCALE + ": " + text);
                }
                continue;
            }
            fractionalPart = Math.addExact(Math.multiplyExact(fractionalPart, 10L), digit);
            fractionalDigits++;
        }
        if (!digitSeen) {
            throw new IllegalArgumentException("invalid numeric value: " + text);
        }

        long scaledValue = Math.multiplyExact(wholePart, SCALE_FACTOR);
        for (int i = fractionalDigits; i < SCALE; i++) {
            fractionalPart = Math.multiplyExact(fractionalPart, 10L);
        }
        scaledValue = Math.addExact(scaledValue, fractionalPart);
        return negative ? -scaledValue : scaledValue;
    }

    public static String format(final long scaledValue) {
        final boolean negative = scaledValue < 0L;
        final long wholePart = Math.abs(scaledValue / SCALE_FACTOR);
        long fractionalPart = Math.abs(scaledValue % SCALE_FACTOR);
        if (fractionalPart == 0L) {
            return (negative ? "-" : "") + wholePart + ".0";
        }

        final char[] fractionDigits = new char[SCALE];
        for (int i = SCALE - 1; i >= 0; i--) {
            fractionDigits[i] = (char) ('0' + (fractionalPart % 10L));
            fractionalPart /= 10L;
        }

        int trimEnd = SCALE;
        while (trimEnd > 1 && fractionDigits[trimEnd - 1] == '0') {
            trimEnd--;
        }

        final StringBuilder builder = new StringBuilder(32);
        if (negative) {
            builder.append('-');
        }
        builder.append(wholePart).append('.');
        builder.append(fractionDigits, 0, trimEnd);
        return builder.toString();
    }
}
