package ua.edu.nupp.kpp.waste.server.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PriorityCalculatorTest {
    private static BigDecimal decimal(String value) { return new BigDecimal(value); }

    @Test void usesAllThreeWeights() {
        assertEquals(decimal("65.50"), PriorityCalculator.calculate(decimal("80"), decimal("60"), 36));
    }

    @Test void acceptsAllFactorsAtZero() {
        assertEquals(decimal("0.00"), PriorityCalculator.calculate(decimal("0"), decimal("0"), 0));
    }

    @Test void capsOverdueHoursAtSeventyTwo() {
        assertEquals(decimal("100.00"), PriorityCalculator.normalizeOverdue(72));
        assertEquals(decimal("100.00"), PriorityCalculator.normalizeOverdue(8760));
        assertEquals(decimal("100.00"), PriorityCalculator.calculate(decimal("100"), decimal("100"), 8760));
    }

    @Test void roundsNormalizedOverdueFactorBeforeApplyingWeight() {
        assertEquals(decimal("1.39"), PriorityCalculator.normalizeOverdue(1));
        assertEquals(decimal("0.35"), PriorityCalculator.calculate(decimal("0"), decimal("0"), 1));
    }

    @Test void roundsInputAndMidpointResultInDecimalArithmetic() {
        assertEquals(decimal("1.01"), Validation.score(decimal("1.005"), "factor"));
        assertEquals(decimal("0.04"), PriorityCalculator.calculate(decimal("0"), decimal("0.10"), 0));
    }

    @Test void rejectsInvalidFillAndRiskBounds() {
        assertThrows(ApiException.class, () -> PriorityCalculator.calculate(decimal("-0.01"), decimal("40"), 0));
        assertThrows(ApiException.class, () -> PriorityCalculator.calculate(decimal("90"), decimal("100.01"), 1));
        assertThrows(ApiException.class, () -> PriorityCalculator.calculate(null, decimal("40"), 0));
    }

    @Test void rejectsMissingOrInvalidOverdueHours() {
        assertThrows(ApiException.class, () -> PriorityCalculator.normalizeOverdue(null));
        assertThrows(ApiException.class, () -> PriorityCalculator.normalizeOverdue(-1));
        assertThrows(ApiException.class, () -> PriorityCalculator.normalizeOverdue(8761));
    }
}
