package ua.edu.nupp.kpp.waste.client;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class InputTest {
    @Test void acceptsDecimalCommaAndPreservesTwoDecimalPlaces() {
        assertEquals(new BigDecimal("87.25"), Input.percent("87,25", "F1"));
    }

    @Test void refusesScientificNotationAndOutOfRangePercentage() {
        assertThrows(IllegalArgumentException.class, () -> Input.percent("1e2", "F1"));
        assertThrows(IllegalArgumentException.class, () -> Input.percent("100,01", "F1"));
        assertThrows(IllegalArgumentException.class, () -> Input.percent("-0.01", "F1"));
    }

    @Test void refusesFractionalHours() {
        assertThrows(IllegalArgumentException.class, () -> Input.whole("2.5", "Години", 0, 8760));
    }

    @Test void trimsAndRejectsBlankRequiredNames() {
        assertEquals("Майданчик", Input.text("  Майданчик   ", "Назва", 100));
        assertThrows(IllegalArgumentException.class, () -> Input.text("  ", "Назва", 100));
    }
}
