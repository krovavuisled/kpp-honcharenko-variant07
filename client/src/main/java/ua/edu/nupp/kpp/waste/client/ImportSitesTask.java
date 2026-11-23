package ua.edu.nupp.kpp.waste.client;

import javafx.concurrent.Task;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class ImportSitesTask extends Task<ImportSitesTask.Result> {
    private static final Logger LOGGER = Logger.getLogger(ImportSitesTask.class.getName());
    private final Path csv;
    private final ApiClient api;

    public ImportSitesTask(Path csv, ApiClient api) {
        this.csv = csv;
        this.api = api;
    }

    public record Result(int accepted, int rejected, int remaining, List<String> errors, String interruptedBy) {}

    @Override protected Result call() throws IOException {
        LOGGER.info("Почато фоновий імпорт контейнерних майданчиків.");
        updateMessage("Читання CSV…");
        var rows = SiteCsv.read(csv);
        updateProgress(0, Math.max(rows.size(), 1));
        int accepted = 0;
        int rejected = 0;
        int processed = 0;
        List<String> errors = new ArrayList<>();
        String interruptedBy = null;
        for (var row : rows) {
            if (isCancelled()) break;
            if (!row.valid()) {
                rejected++;
                errors.add("Рядок " + row.line() + ": " + row.error());
            } else {
                try {
                    api.save("sites", null, row.site());
                    accepted++;
                } catch (ApiClient.ApiException e) {
                    if (e.status() < 400 || e.status() >= 500) {
                        interruptedBy = "Рядок " + row.line() + ": " + e.getMessage();
                        LOGGER.warning("Імпорт зупинено через недоступність API.");
                        break;
                    }
                    rejected++;
                    errors.add("Рядок " + row.line() + ": " + e.getMessage());
                }
            }
            processed++;
            updateProgress(processed, rows.size());
            updateMessage("Оброблено " + processed + " із " + rows.size() + " рядків");
        }
        if (rows.isEmpty()) updateProgress(1, 1);
        LOGGER.info("Імпорт майданчиків: додано " + accepted + ", відхилено " + rejected +
                ", не оброблено " + (rows.size() - processed) + ".");
        return new Result(accepted, rejected, rows.size() - processed, List.copyOf(errors), interruptedBy);
    }
}
