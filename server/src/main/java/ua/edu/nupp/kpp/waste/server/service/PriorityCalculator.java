package ua.edu.nupp.kpp.waste.server.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PriorityCalculator {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal HOURS_AT_MAX = new BigDecimal("72");
    private static final BigDecimal FILL_WEIGHT = new BigDecimal("0.40");
    private static final BigDecimal RISK_WEIGHT = new BigDecimal("0.35");
    private static final BigDecimal OVERDUE_WEIGHT = new BigDecimal("0.25");

    private PriorityCalculator() { }

    public static BigDecimal normalizeOverdue(Integer hours) {
        int count = Validation.integer(hours, "Години прострочення", 0, 8760);
        if (count >= 72) return HUNDRED.setScale(2);
        return BigDecimal.valueOf(count).multiply(HUNDRED).divide(HOURS_AT_MAX, 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal calculate(BigDecimal fillLevel, BigDecimal sanitaryRisk, Integer overdueHours) {
        BigDecimal fill = Validation.score(fillLevel, "Рівень заповнення");
        BigDecimal risk = Validation.score(sanitaryRisk, "Санітарний ризик");
        BigDecimal overdue = normalizeOverdue(overdueHours);
        return fill.multiply(FILL_WEIGHT)
                .add(risk.multiply(RISK_WEIGHT))
                .add(overdue.multiply(OVERDUE_WEIGHT))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
