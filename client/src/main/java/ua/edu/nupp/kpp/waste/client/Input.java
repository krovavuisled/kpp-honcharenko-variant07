package ua.edu.nupp.kpp.waste.client;

import java.math.BigDecimal;

/** Form checks offer quick feedback. The server still validates every request. */
public final class Input {
    private Input() {}

    public static String text(String value, String label, int max) {
        String cleaned = value == null ? "" : value.strip();
        if (cleaned.isEmpty()) throw new IllegalArgumentException("Заповніть поле «" + label + "».");
        return optional(cleaned, label, max);
    }

    public static String optional(String value, String label, int max) {
        String cleaned = value == null ? "" : value.strip();
        if (cleaned.length() > max) throw new IllegalArgumentException("Поле «" + label + "» має містити до " + max + " символів.");
        return cleaned;
    }

    public static int whole(String value, String label, int min, int max) {
        try {
            int number = Integer.parseInt(value.strip());
            if (number >= min && number <= max) return number;
        } catch (NumberFormatException ignored) {}
        throw new IllegalArgumentException("Поле «" + label + "» має бути цілим числом від " + min + " до " + max + ".");
    }

    public static BigDecimal percent(String value, String label) {
        try {
            String normalized = value.strip().replace(',', '.');
            if (normalized.matches("[0-9]{1,3}(?:\\.[0-9]{1,2})?")) {
                BigDecimal percent = new BigDecimal(normalized);
                if (percent.compareTo(BigDecimal.valueOf(100)) <= 0) return percent;
            }
        } catch (NumberFormatException ignored) {}
        throw new IllegalArgumentException("Поле «" + label + "» має бути числом від 0 до 100, до двох знаків після коми.");
    }
}
