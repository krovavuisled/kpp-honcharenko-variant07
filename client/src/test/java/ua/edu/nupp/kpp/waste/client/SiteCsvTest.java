package ua.edu.nupp.kpp.waste.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SiteCsvTest {
    @TempDir Path directory;
    private static final String HEADER = "code;name;address;capacityContainers\n";

    private Path csv(String content) throws IOException {
        return Files.writeString(directory.resolve("input.csv"), content);
    }

    @Test void parsesUtf8WithBomAndUkrainianText() throws IOException {
        var rows = SiteCsv.read(csv("\uFEFF" + HEADER + "W-01;Площа Миру;Торгова, 12;6\n"));
        assertEquals(1, rows.size());
        assertEquals(new Models.Site(null, "W-01", "Площа Миру", "Торгова, 12", 6), rows.getFirst().site());
    }

    @Test void handlesQuotedSemicolonAndEscapedQuotationMarks() throws IOException {
        var row = SiteCsv.read(csv(HEADER + "W-01;\"Зона \"\"А\"\"; сектор\";Торгова, 12;6")).getFirst();
        assertEquals("Зона \"А\"; сектор", row.site().name());
    }

    @Test void invalidHeaderRefusesFile() throws IOException {
        Path input = csv("name,address,containers\n");
        assertThrows(IllegalArgumentException.class, () -> SiteCsv.read(input));
    }

    @Test void missingFileReportsIoError() {
        Path input = directory.resolve("missing.csv");
        assertThrows(NoSuchFileException.class, () -> SiteCsv.read(input));
    }

    @Test void rejectsWrongFieldCountAndNonNumericCapacity() throws IOException {
        var rows = SiteCsv.read(csv(HEADER + "A;Майданчик;Адреса\nB;Майданчик;Адреса;abc"));
        assertEquals(2, rows.size());
        assertEquals(2, rows.getFirst().line());
        assertFalse(rows.getFirst().valid());
        assertTrue(rows.getFirst().error().contains("4 поля"));
        assertEquals(3, rows.getLast().line());
        assertFalse(rows.getLast().valid());
        assertTrue(rows.getLast().error().contains("Контейнери"));
    }

    @Test void missingRequiredFieldRejectsOnlyOneRowAndPreservesLineNumbers() throws IOException {
        var rows = SiteCsv.read(csv(HEADER + "W-01;Назва;;11\n\nW-02;Назва;Миру, 2;4"));
        assertEquals(2, rows.size());
        assertFalse(rows.getFirst().valid());
        assertTrue(rows.getFirst().error().contains("Адреса"));
        assertEquals(2, rows.getFirst().line());
        assertEquals(4, rows.getLast().line());
        assertTrue(rows.getLast().valid());
    }

    @Test void malformedQuotedRowDoesNotPreventFollowingRowFromBeingRead() throws IOException {
        var rows = SiteCsv.read(csv(HEADER + "W-01;\"Незакрита назва;Миру, 2;4\nW-02;Назва;Миру, 2;4"));
        assertFalse(rows.getFirst().valid());
        assertTrue(rows.getFirst().error().contains("лапки"));
        assertTrue(rows.getLast().valid());
    }

    @Test void capacityAcceptsBothBoundsAndRejectsValuesOutsideThem() throws IOException {
        var rows = SiteCsv.read(csv(HEADER + "A;Майданчик;Перший;1\nB;Майданчик;Перший;500\n" +
                "C;Майданчик;Перший;0\nD;Майданчик;Перший;501"));
        assertEquals(1, rows.get(0).site().capacityContainers());
        assertEquals(500, rows.get(1).site().capacityContainers());
        assertFalse(rows.get(2).valid());
        assertFalse(rows.get(3).valid());
    }

    @Test void shippedMixedExampleDeclaresTwoValidAndTwoRejectedRows() throws IOException {
        var rows = SiteCsv.read(Path.of("sample-data", "sites-with-errors.csv"));
        assertEquals(4, rows.size());
        assertTrue(rows.get(0).valid());
        assertFalse(rows.get(1).valid());
        assertTrue(rows.get(2).valid());
        assertFalse(rows.get(3).valid());
    }
}
