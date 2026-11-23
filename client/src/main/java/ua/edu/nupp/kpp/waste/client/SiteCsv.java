package ua.edu.nupp.kpp.waste.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static ua.edu.nupp.kpp.waste.client.Models.Site;

/** A UTF-8, semicolon-delimited file with one physical line per record. */
public final class SiteCsv {
    private static final List<String> HEADER = List.of("code", "name", "address", "capacityContainers");
    private SiteCsv() {}

    public record Row(int line, Site site, String error) {
        public boolean valid() { return site != null; }
    }

    public static List<Row> read(Path file) throws IOException {
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String first = reader.readLine();
            if (first != null && first.startsWith("\uFEFF")) first = first.substring(1);
            if (first == null || !split(first).equals(HEADER)) {
                throw new IllegalArgumentException("Заголовок CSV має бути code;name;address;capacityContainers.");
            }
            List<Row> rows = new ArrayList<>();
            String line;
            int number = 1;
            while ((line = reader.readLine()) != null) {
                number++;
                if (line.isBlank()) continue;
                try {
                    var fields = split(line);
                    if (fields.size() != HEADER.size()) throw new IllegalArgumentException("Очікуються рівно 4 поля.");
                    var site = new Site(null, Input.text(fields.get(0), "Код", 24),
                            Input.text(fields.get(1), "Назва", 100), Input.text(fields.get(2), "Адреса", 180),
                            Input.whole(fields.get(3), "Контейнери", 1, 500));
                    rows.add(new Row(number, site, null));
                } catch (IllegalArgumentException e) {
                    rows.add(new Row(number, null, e.getMessage()));
                }
            }
            return List.copyOf(rows);
        }
    }

    static List<String> split(String line) {
        var fields = new ArrayList<String>();
        var cell = new StringBuilder();
        boolean quoted = false;
        boolean closed = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch != '"') cell.append(ch);
                else if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    quoted = false;
                    closed = true;
                }
            } else if (ch == ';') {
                fields.add(cell.toString());
                cell.setLength(0);
                closed = false;
            } else if (closed) {
                throw new IllegalArgumentException("Зайвий символ після закривальних лапок.");
            } else if (ch == '"') {
                if (!cell.isEmpty()) throw new IllegalArgumentException("Лапки дозволені лише на початку поля.");
                quoted = true;
            } else cell.append(ch);
        }
        if (quoted) throw new IllegalArgumentException("Незакриті лапки.");
        fields.add(cell.toString());
        return fields;
    }
}
