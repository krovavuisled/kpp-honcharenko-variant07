package ua.edu.nupp.kpp.waste.client;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

import static ua.edu.nupp.kpp.waste.client.Models.*;

public final class MainApp extends Application {
    private static final Logger LOGGER = Logger.getLogger(MainApp.class.getName());
    private final ObservableList<Request> requests = FXCollections.observableArrayList();
    private final ObservableList<Site> sites = FXCollections.observableArrayList();
    private final ObservableList<Vehicle> vehicles = FXCollections.observableArrayList();
    private final ObservableList<Trip> trips = FXCollections.observableArrayList();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "waste-dispatch-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final javafx.beans.property.BooleanProperty busy = new javafx.beans.property.SimpleBooleanProperty();
    private final Label status = label("Підключення до сервера…", "status-line");
    private final Label requestCount = label("—", "count");
    private final Label activeCount = label("—", "count");
    private final Label siteCount = label("—", "count");
    private final Label vehicleCount = label("—", "count");
    private final Label importSummary = label("Виберіть CSV із переліком майданчиків.", "import-detail");
    private final ProgressBar importProgress = new ProgressBar(0);
    private final TextArea importErrors = new TextArea();
    private final TabPane tabs = new TabPane();
    private ApiClient api;
    private Stage stage;
    private Scene scene;
    private CrudView<Request> requestView;
    private CrudView<Site> siteView;
    private CrudView<Vehicle> vehicleView;
    private CrudView<Trip> tripView;
    private boolean captureStarted;

    @Override public void start(Stage stage) {
        this.stage = stage;
        api = new ApiClient();
        requestView = buildRequests();
        siteView = buildSites();
        vehicleView = buildVehicles();
        tripView = buildTrips();
        tabs.getTabs().addAll(tab("Заявки", requestView), tab("Майданчики", siteView),
                tab("Транспорт", vehicleView), tab("Виїзди", tripView));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        var refresh = new Button("Оновити дані");
        refresh.getStyleClass().add("refresh-button");
        refresh.disableProperty().bind(busy);
        refresh.setOnAction(event -> reload("Дані оновлено."));
        VBox heading = new VBox(3, label("СЛУЖБА ВИВЕЗЕННЯ ВІДХОДІВ", "brand-kicker"),
                label("Диспетчерська заявок", "brand-title"));
        VBox identity = new VBox(4, label("Гончаренко Олександр", "identity"),
                label("204-ТН  ·  Варіант 7", "identity-detail"));
        identity.setAlignment(Pos.CENTER_RIGHT);
        HBox hero = new HBox(20, heading, spacer(), identity, refresh);
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.getStyleClass().add("hero");
        HBox metrics = new HBox(12,
                metric("Усі заявки", requestCount),
                metric("Незавершені", activeCount),
                metric("Майданчики", siteCount),
                metric("Активний транспорт", vehicleCount));
        metrics.getStyleClass().add("metrics");

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(16, 16);
        spinner.setMaxSize(16, 16);
        spinner.visibleProperty().bind(busy);
        spinner.managedProperty().bind(spinner.visibleProperty());
        status.setWrapText(true);
        HBox footer = new HBox(9, spinner, status);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("footer");
        BorderPane root = new BorderPane(tabs, new VBox(hero, metrics), null, footer, null);
        root.getStyleClass().add("application");
        var desktop = Screen.getPrimary().getVisualBounds();
        scene = new Scene(root, Math.min(1360, desktop.getWidth() - 24), Math.min(820, desktop.getHeight() - 42));
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/application.css")).toExternalForm());
        stage.setScene(scene);
        stage.setTitle("Вивезення відходів · Гончаренко Олександр · Варіант 7");
        stage.setMinWidth(920);
        stage.setMinHeight(620);
        stage.show();
        reload("Підключено. Дані завантажено.");
    }

    private CrudView<Request> buildRequests() {
        TextField title = field("Наприклад, заповнено контейнери біля ринку");
        ComboBox<Site> site = choices(sites, v -> v.code() + " · " + v.name());
        TextField fill = field("0–100");
        TextField risk = field("0–100");
        TextField overdue = field("0–8760");
        Label details = label("Нова заявка отримає стан «Створено».", "request-details");
        details.setWrapText(true);
        VBox fields = new VBox(10, detail("Тема", title), detail("Майданчик", site),
                paired("Заповнення F1, %", fill, "Санітарний ризик F2", risk),
                detail("Години прострочення для F3", overdue), details);
        CrudView<Request> view = new CrudView<>("Заявки на вивезення", "Виберіть запис або створіть нову заявку.",
                "requests", requests, fields, old -> new Request(old == null ? null : old.id(),
                selection(site, "Майданчик").id(), Input.text(title.getText(), "Тема", 140),
                Input.percent(fill.getText(), "Заповнення"), Input.percent(risk.getText(), "Санітарний ризик"),
                Input.whole(overdue.getText(), "Години прострочення", 0, 8760), null, null),
                value -> {
                    title.setText(value == null ? "" : value.title());
                    pick(site, value == null ? null : value.siteId());
                    fill.setText(value == null ? "" : decimal(value.fillLevel()));
                    risk.setText(value == null ? "" : decimal(value.sanitaryRisk()));
                    overdue.setText(value == null ? "0" : Integer.toString(value.overdueHours()));
                    details.setText(value == null ? "Нова заявка отримає стан «Створено»." :
                            "Індекс: " + decimal(value.priority()) + "  ·  Стан: " + statusLabel(value.status()));
                });
        view.table.getColumns().addAll(List.of(
                column("ID", Request::id, 55),
                column("Тема заявки", Request::title, 250),
                column("Майданчик", r -> siteCode(r.siteId()), 120),
                statusColumn(), numberColumn("Пріоритет", Request::priority, 90)));
        Button advance = new Button("Наступний стан");
        advance.getStyleClass().add("advance-button");
        advance.disableProperty().bind(busy.or(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> view.selected() == null || view.selected().status() == null || view.selected().status().next() == null,
                view.table.getSelectionModel().selectedItemProperty())));
        advance.setOnAction(event -> {
            var selected = view.selected();
            if (selected == null || selected.status() == null || selected.status().next() == null) return;
            RequestStatus next = selected.status().next();
            if (!confirm("Змінити стан заявки", "Перевести заявку № " + selected.id() + " у стан «" + next.label() + "»?")) return;
            mutate(() -> api.advance(selected.id(), next), "Заявка № " + selected.id() + ": " + next.label() + ".");
        });
        Label hint = label("Для планування спершу створіть виїзд і виберіть активний транспорт.", "muted");
        hint.setWrapText(true);
        view.form.getChildren().addAll(new Separator(), advance, hint);
        return view;
    }

    private CrudView<Site> buildSites() {
        TextField code = field("Наприклад, MP-05");
        TextField name = field("Назва майданчика");
        TextField address = field("Вулиця, номер");
        TextField capacity = field("1–500");
        VBox fields = new VBox(10, detail("Код", code), detail("Назва", name),
                detail("Адреса", address), detail("Кількість контейнерів", capacity));
        CrudView<Site> view = new CrudView<>("Контейнерні майданчики", "Довідник місць збору. Можна завантажити CSV.",
                "sites", sites, fields, old -> new Site(old == null ? null : old.id(),
                Input.text(code.getText(), "Код", 24), Input.text(name.getText(), "Назва", 100),
                Input.text(address.getText(), "Адреса", 180),
                Input.whole(capacity.getText(), "Кількість контейнерів", 1, 500)),
                v -> {
                    code.setText(v == null ? "" : v.code());
                    name.setText(v == null ? "" : v.name());
                    address.setText(v == null ? "" : v.address());
                    capacity.setText(v == null ? "" : Integer.toString(v.capacityContainers()));
                });
        view.table.getColumns().addAll(List.of(column("Код", Site::code, 100), column("Назва", Site::name, 180),
                column("Адреса", Site::address, 255), column("Контейнери", Site::capacityContainers, 108)));
        Button importButton = new Button("Обрати CSV…");
        importButton.getStyleClass().add("secondary-button");
        importButton.disableProperty().bind(busy);
        importProgress.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(importProgress, Priority.ALWAYS);
        importSummary.setWrapText(true);
        importErrors.setEditable(false);
        importErrors.setWrapText(true);
        importErrors.setPrefRowCount(3);
        importErrors.setPromptText("Повідомлення про відхилені рядки з’являться тут.");
        importButton.setOnAction(event -> {
            var chooser = new FileChooser();
            chooser.setTitle("Імпорт контейнерних майданчиків");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Таблиці CSV", "*.csv"));
            var chosen = chooser.showOpenDialog(stage);
            if (chosen != null) startImport(chosen.toPath(), () -> {});
        });
        VBox importPanel = new VBox(8, label("Імпорт майданчиків", "import-title"),
                new HBox(12, importButton, importProgress), importSummary, importErrors);
        importPanel.getStyleClass().add("import-panel");
        view.setBottom(importPanel);
        return view;
    }

    private CrudView<Vehicle> buildVehicles() {
        TextField plate = field("Наприклад, BI 4821 KA");
        TextField model = field("Марка й модель");
        TextField capacity = field("1–50000");
        CheckBox active = new CheckBox("Доступний для виїздів");
        VBox fields = new VBox(12, detail("Державний номер", plate), detail("Модель", model),
                detail("Вантажопідйомність, кг", capacity), active);
        CrudView<Vehicle> view = new CrudView<>("Транспортні засоби", "Вибирайте доступний транспорт для планування виїзду.",
                "vehicles", vehicles, fields, old -> new Vehicle(old == null ? null : old.id(),
                Input.text(plate.getText(), "Державний номер", 24), Input.text(model.getText(), "Модель", 100),
                Input.whole(capacity.getText(), "Вантажопідйомність", 1, 50000), active.isSelected()),
                v -> {
                    plate.setText(v == null ? "" : v.plate());
                    model.setText(v == null ? "" : v.model());
                    capacity.setText(v == null ? "" : Integer.toString(v.capacityKg()));
                    active.setSelected(v == null || v.active());
                });
        view.table.getColumns().addAll(List.of(column("Державний номер", Vehicle::plate, 145),
                column("Модель", Vehicle::model, 205), column("Вантаж, кг", Vehicle::capacityKg, 125),
                column("Доступність", v -> v.active() ? "Активний" : "Недоступний", 130)));
        return view;
    }

    private CrudView<Trip> buildTrips() {
        ComboBox<Request> request = choices(requests, v -> "№ " + v.id() + " · " + v.title());
        ComboBox<Vehicle> vehicle = choices(vehicles, v -> v.plate() + (v.active() ? "" : " · недоступний"));
        DatePicker date = new DatePicker();
        date.setEditable(false);
        date.setMaxWidth(Double.MAX_VALUE);
        TextArea notes = new TextArea();
        notes.setPromptText("Необов’язково");
        notes.setWrapText(true);
        notes.setPrefRowCount(3);
        VBox fields = new VBox(10, detail("Заявка", request), detail("Транспорт", vehicle),
                detail("Дата виїзду", date), detail("Примітки", notes));
        CrudView<Trip> view = new CrudView<>("Виїзди", "За кожною заявкою можна зареєструвати один виїзд.",
                "trips", trips, fields, old -> {
                    LocalDate day = date.getValue();
                    if (day == null) throw new IllegalArgumentException("Оберіть дату виїзду.");
                    return new Trip(old == null ? null : old.id(), selection(request, "Заявка").id(),
                            selection(vehicle, "Транспорт").id(), day.toString(),
                            Input.optional(notes.getText(), "Примітки", 240));
                }, v -> {
                    pick(request, v == null ? null : v.requestId());
                    pick(vehicle, v == null ? null : v.vehicleId());
                    date.setValue(v == null ? LocalDate.now() : LocalDate.parse(v.scheduledDate()));
                    notes.setText(v == null ? "" : v.notes());
                });
        view.table.getColumns().addAll(List.of(column("ID", Trip::id, 55),
                column("Заявка", t -> requestName(t.requestId()), 245),
                column("Транспорт", t -> vehiclePlate(t.vehicleId()), 125),
                column("Дата", Trip::scheduledDate, 110), column("Примітки", Trip::notes, 190)));
        return view;
    }

    private void startImport(Path csv, Runnable onFinish) {
        if (busy.get()) return;
        var task = new ImportSitesTask(csv, api);
        importErrors.clear();
        importProgress.progressProperty().bind(task.progressProperty());
        importSummary.textProperty().bind(task.messageProperty());
        run(task, result -> {
            unbindImport();
            int processed = result.accepted() + result.rejected();
            importProgress.setProgress(result.remaining() == 0 ? 1 : (double) processed / (processed + result.remaining()));
            String summary = "Додано " + result.accepted() + "  ·  Відхилено " + result.rejected();
            if (result.remaining() > 0) summary += "  ·  Не оброблено " + result.remaining();
            importSummary.setText(summary);
            String report = String.join("\n", result.errors());
            if (result.interruptedBy() != null) report += (report.isEmpty() ? "" : "\n") + result.interruptedBy();
            importErrors.setText(report);
            reload("Імпорт завершено. " + summary, onFinish);
        }, error -> {
            unbindImport();
            importProgress.setProgress(0);
            importSummary.setText("Не вдалося прочитати CSV.");
            importErrors.setText(error instanceof IOException
                    ? "Перевірте доступ до файлу та кодування UTF-8."
                    : error.getMessage());
            status.setText("Імпорт не виконано.");
            LOGGER.warning("Читання CSV завершилося помилкою.");
        });
    }

    private void unbindImport() {
        importProgress.progressProperty().unbind();
        importSummary.textProperty().unbind();
    }

    private void mutate(Runnable write, String success) {
        Task<Void> task = task(() -> { write.run(); return null; });
        status.setText("Застосовуємо зміни…");
        run(task, result -> reload(success), error -> showError("Операцію не виконано", error));
    }

    private record Overview(List<Site> sites, List<Vehicle> vehicles, List<Request> requests, List<Trip> trips) {}
    private void reload(String success) { reload(success, () -> {}); }

    private void reload(String success, Runnable onSuccess) {
        var task = task(() -> new Overview(api.list("sites", Site.class), api.list("vehicles", Vehicle.class),
                api.list("requests", Request.class), api.list("trips", Trip.class)));
        run(task, data -> {
            Long requestId = selectedId(requestView.selected());
            Long siteId = selectedId(siteView.selected());
            Long vehicleId = selectedId(vehicleView.selected());
            Long tripId = selectedId(tripView.selected());
            sites.setAll(data.sites());
            vehicles.setAll(data.vehicles());
            requests.setAll(data.requests());
            trips.setAll(data.trips());
            requestView.restoreSelection(requestId);
            siteView.restoreSelection(siteId);
            vehicleView.restoreSelection(vehicleId);
            tripView.restoreSelection(tripId);
            requestCount.setText(Integer.toString(requests.size()));
            activeCount.setText(Long.toString(requests.stream().filter(r -> r.status() != RequestStatus.COMPLETED).count()));
            siteCount.setText(Integer.toString(sites.size()));
            vehicleCount.setText(Long.toString(vehicles.stream().filter(Vehicle::active).count()));
            status.setText(success);
            captureScreensOnce();
            onSuccess.run();
        }, error -> showError("Не вдалося оновити дані", error));
    }

    private <T> void run(Task<T> task, Consumer<T> success, Consumer<Throwable> failure) {
        if (busy.get()) return;
        busy.set(true);
        task.setOnSucceeded(e -> {
            busy.set(false);
            success.accept(task.getValue());
        });
        task.setOnFailed(e -> {
            busy.set(false);
            failure.accept(task.getException());
        });
        task.setOnCancelled(e -> busy.set(false));
        executor.execute(task);
    }

    private static <T> Task<T> task(Callable<T> action) {
        return new Task<>() {
            @Override protected T call() throws Exception { return action.call(); }
        };
    }

    private void showError(String title, Throwable error) {
        String message = error instanceof ApiClient.ApiException || error instanceof IllegalArgumentException
                ? error.getMessage() : "Спробуйте ще раз або перевірте журнал застосунку.";
        status.setText(title + ". " + message);
        LOGGER.warning(() -> title + ": " + error.getClass().getSimpleName());
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(stage);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private boolean confirm(String title, String message) {
        var yes = new ButtonType("Підтвердити", ButtonBar.ButtonData.OK_DONE);
        var no = new ButtonType("Скасувати", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, message, yes, no);
        confirm.initOwner(stage);
        confirm.setTitle(title);
        confirm.setHeaderText(title);
        return confirm.showAndWait().filter(yes::equals).isPresent();
    }

    private final class CrudView<T extends Entity> extends BorderPane {
        final TableView<T> table;
        final VBox form;
        private final Consumer<T> fill;

        CrudView(String heading, String description, String resource, ObservableList<T> data, Node fields,
                 Function<T, T> read, Consumer<T> fill) {
            this.fill = fill;
            table = new TableView<>(data);
            table.setPlaceholder(new Label("Немає записів. Додайте новий або оновіть дані."));
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            table.getStyleClass().add("registry");
            VBox text = new VBox(4, label(heading, "section-title"), label(description, "muted"));
            Button add = new Button("Новий запис");
            add.getStyleClass().add("secondary-button");
            add.disableProperty().bind(busy);
            add.setOnAction(e -> {
                table.getSelectionModel().clearSelection();
                fill.accept(null);
                status.setText("Заповніть форму нового запису.");
            });
            HBox listHeader = new HBox(12, text, spacer(), add);
            listHeader.setAlignment(Pos.CENTER_LEFT);
            VBox listPane = new VBox(12, listHeader, table);
            listPane.getStyleClass().add("list-pane");
            VBox.setVgrow(table, Priority.ALWAYS);

            Label formTitle = label("Новий запис", "form-title");
            Button save = new Button("Зберегти");
            save.getStyleClass().add("save-button");
            save.disableProperty().bind(busy);
            save.setOnAction(e -> {
                try {
                    T current = selected();
                    T payload = read.apply(current);
                    mutate(() -> api.save(resource, current == null ? null : current.id(), payload),
                            current == null ? "Запис додано." : "Зміни збережено.");
                } catch (IllegalArgumentException error) {
                    showError("Перевірте введені дані", error);
                }
            });
            Button delete = new Button("Видалити");
            delete.getStyleClass().add("delete-button");
            delete.disableProperty().bind(busy.or(table.getSelectionModel().selectedItemProperty().isNull()));
            delete.setOnAction(e -> {
                T selected = selected();
                if (selected != null && confirm("Видалення", "Видалити запис № " + selected.id() + "?")) {
                    mutate(() -> api.delete(resource, selected.id()), "Запис видалено.");
                }
            });
            fields.disableProperty().bind(busy);
            HBox buttons = new HBox(9, save, delete);
            form = new VBox(16, formTitle, fields, buttons);
            form.getStyleClass().add("editor");
            ScrollPane scroll = new ScrollPane(form);
            scroll.getStyleClass().add("editor-scroll");
            scroll.setFitToWidth(true);
            scroll.setMinWidth(315);
            scroll.setPrefWidth(345);
            scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            SplitPane split = new SplitPane(listPane, scroll);
            split.setOrientation(Orientation.HORIZONTAL);
            split.setDividerPositions(0.72);
            setCenter(split);
            setPadding(new Insets(12, 18, 14, 18));
            table.getSelectionModel().selectedItemProperty().addListener((observable, old, value) -> {
                formTitle.setText(value == null ? "Новий запис" : "Редагування № " + value.id());
                fill.accept(value);
            });
            fill.accept(null);
        }

        T selected() { return table.getSelectionModel().getSelectedItem(); }

        void restoreSelection(Long id) {
            if (id != null) {
                for (T value : table.getItems()) {
                    if (id.equals(value.id())) {
                        table.getSelectionModel().select(value);
                        fill.accept(value);
                        return;
                    }
                }
            }
            table.getSelectionModel().clearSelection();
            fill.accept(null);
        }
    }

    private void captureScreensOnce() {
        String folder = System.getProperty("app.screenshotDir");
        if (captureStarted || folder == null || folder.isBlank()) return;
        captureStarted = true;
        var path = Path.of(folder);
        String[] files = {"01-requests.png", "02-sites.png", "03-vehicles.png", "04-trips.png"};
        captureTab(path, files, 0);
    }

    private void captureTab(Path path, String[] files, int index) {
        if (index >= files.length) {
            String csv = System.getProperty("app.screenshotImportFile");
            if (csv == null || csv.isBlank()) tabs.getSelectionModel().select(0);
            else {
                tabs.getSelectionModel().select(1);
                startImport(Path.of(csv), () -> afterPaint(() -> saveCapture(path, "05-import.png")));
            }
            return;
        }
        tabs.getSelectionModel().select(index);
        afterPaint(() -> {
            saveCapture(path, files[index]);
            captureTab(path, files, index + 1);
        });
    }

    private static void afterPaint(Runnable action) {
        PauseTransition pause = new PauseTransition(Duration.millis(550));
        pause.setOnFinished(e -> action.run());
        pause.play();
    }

    private void saveCapture(Path directory, String name) {
        try {
            Files.createDirectories(directory);
            ImageIO.write(SwingFXUtils.fromFXImage(scene.snapshot(null), null), "png", directory.resolve(name).toFile());
        } catch (IOException error) {
            LOGGER.warning("Не вдалося зберегти знімок застосунку.");
        }
    }

    @Override public void stop() { executor.shutdownNow(); }
    public static void main(String[] args) { launch(args); }

    private String siteCode(Long id) {
        return sites.stream().filter(v -> Objects.equals(v.id(), id)).map(Site::code).findFirst().orElse("№ " + id);
    }
    private String requestName(Long id) {
        return requests.stream().filter(v -> Objects.equals(v.id(), id)).map(v -> "№ " + id + " · " + v.title())
                .findFirst().orElse("№ " + id);
    }
    private String vehiclePlate(Long id) {
        return vehicles.stream().filter(v -> Objects.equals(v.id(), id)).map(Vehicle::plate).findFirst().orElse("№ " + id);
    }
    private static String statusLabel(RequestStatus value) { return value == null ? "—" : value.label(); }
    private static String decimal(BigDecimal value) { return value == null ? "—" : value.toPlainString().replace('.', ','); }
    private static Long selectedId(Entity entity) { return entity == null ? null : entity.id(); }
    private static Tab tab(String name, Node view) { return new Tab(name, view); }

    private static <T, V> TableColumn<T, V> column(String name, Function<T, V> value, double width) {
        TableColumn<T, V> col = new TableColumn<>(name);
        col.setPrefWidth(width);
        col.setCellValueFactory(row -> new ReadOnlyObjectWrapper<>(value.apply(row.getValue())));
        return col;
    }

    private static <T> TableColumn<T, BigDecimal> numberColumn(String name, Function<T, BigDecimal> value, double width) {
        var col = column(name, value, width);
        col.setCellFactory(v -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : decimal(item));
            }
        });
        return col;
    }

    private static TableColumn<Request, RequestStatus> statusColumn() {
        var col = column("Стан", Request::status, 145);
        col.setCellFactory(v -> new TableCell<>() {
            @Override protected void updateItem(RequestStatus value, boolean empty) {
                super.updateItem(value, empty);
                setText(null);
                setGraphic(empty || value == null ? null : label(value.label(), "state-chip", "state-" + value.name().toLowerCase(java.util.Locale.ROOT)));
            }
        });
        return col;
    }

    private static <T extends Entity> ComboBox<T> choices(ObservableList<T> items, Function<T, String> format) {
        ComboBox<T> input = new ComboBox<>(items);
        input.setPromptText("Оберіть із переліку");
        input.setMaxWidth(Double.MAX_VALUE);
        input.setCellFactory(list -> choiceCell(format));
        input.setButtonCell(choiceCell(format));
        return input;
    }

    private static <T> ListCell<T> choiceCell(Function<T, String> format) {
        return new ListCell<>() {
            @Override protected void updateItem(T value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : format.apply(value));
            }
        };
    }

    private static <T extends Entity> void pick(ComboBox<T> input, Long id) {
        T found = id == null ? null : input.getItems().stream().filter(v -> id.equals(v.id())).findFirst().orElse(null);
        input.setValue(found);
    }

    private static <T> T selection(ComboBox<T> input, String label) {
        if (input.getValue() == null) throw new IllegalArgumentException("Заповніть поле «" + label + "».");
        return input.getValue();
    }

    private static TextField field(String hint) {
        TextField input = new TextField();
        input.setPromptText(hint);
        return input;
    }

    private static VBox detail(String text, Node field) {
        Label label = label(text, "field-label");
        label.setLabelFor(field);
        return new VBox(4, label, field);
    }

    private static HBox paired(String leftLabel, Node left, String rightLabel, Node right) {
        VBox leftBox = detail(leftLabel, left);
        VBox rightBox = detail(rightLabel, right);
        HBox.setHgrow(leftBox, Priority.ALWAYS);
        HBox.setHgrow(rightBox, Priority.ALWAYS);
        return new HBox(8, leftBox, rightBox);
    }

    private static HBox metric(String text, Label count) {
        HBox metric = new HBox(10, count, label(text, "metric-label"));
        metric.setAlignment(Pos.CENTER_LEFT);
        metric.getStyleClass().add("metric");
        metric.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(metric, Priority.ALWAYS);
        return metric;
    }

    private static Label label(String text, String... styles) {
        Label l = new Label(text);
        l.getStyleClass().addAll(styles);
        return l;
    }
    private static Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }
}
