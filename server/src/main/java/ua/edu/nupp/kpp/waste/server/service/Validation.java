package ua.edu.nupp.kpp.waste.server.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

final class Validation {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal HALF_CENT = new BigDecimal("0.005");
    private static final BigDecimal ZERO_SCORE = new BigDecimal("0.00");

    private Validation() { }

    static long id(Long value, String label) {
        if (value == null || value < 1) throw ApiException.badRequest(label + ": потрібен додатний ідентифікатор");
        return value;
    }

    static String required(String value, String label, int max) {
        String normalized = optional(value, label, max);
        if (normalized.isEmpty()) throw ApiException.badRequest(label + ": обов’язкове поле");
        return normalized;
    }

    static String optional(String value, String label, int max) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.length() > max) {
            throw ApiException.badRequest(label + ": дозволено до " + max + " символів");
        }
        return normalized;
    }

    static int integer(Integer value, String label, int min, int max) {
        if (value == null || value < min || value > max) {
            throw ApiException.badRequest(label + ": потрібне ціле число від " + min + " до " + max);
        }
        return value;
    }

    static BigDecimal score(BigDecimal value, String label) {
        if (value == null || value.signum() < 0 || value.compareTo(HUNDRED) > 0) {
            throw ApiException.badRequest(label + ": очікується число від 0 до 100");
        }
        return value.compareTo(HALF_CENT) < 0 ? ZERO_SCORE : value.setScale(2, RoundingMode.HALF_UP);
    }

    static String date(String value) {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw ApiException.badRequest("Дата виїзду: введіть дату у форматі РРРР-ММ-ДД");
        }
        try {
            LocalDate date = LocalDate.parse(value);
            if (date.getYear() < 1000) throw ApiException.badRequest("Рік виїзду має бути від 1000 до 9999");
            return date.toString();
        } catch (DateTimeParseException ex) {
            throw ApiException.badRequest("Дата виїзду не існує в календарі");
        }
    }
}
