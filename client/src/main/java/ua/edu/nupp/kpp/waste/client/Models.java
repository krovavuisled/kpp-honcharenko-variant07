package ua.edu.nupp.kpp.waste.client;

import java.math.BigDecimal;

public final class Models {
    private Models() {}

    public interface Entity {
        Long id();
    }

    public record Site(Long id, String code, String name, String address, int capacityContainers) implements Entity {}
    public record Vehicle(Long id, String plate, String model, int capacityKg, boolean active) implements Entity {}
    public record Request(Long id, Long siteId, String title, BigDecimal fillLevel, BigDecimal sanitaryRisk,
                          int overdueHours, BigDecimal priority, RequestStatus status) implements Entity {}
    public record Trip(Long id, Long requestId, Long vehicleId, String scheduledDate, String notes) implements Entity {}

    public enum RequestStatus {
        CREATED("Створено"), PLANNED("Заплановано"), IN_PROGRESS("Виконується"), COMPLETED("Виконано");

        private final String label;
        RequestStatus(String label) { this.label = label; }
        public String label() { return label; }

        public RequestStatus next() {
            return switch (this) {
                case CREATED -> PLANNED;
                case PLANNED -> IN_PROGRESS;
                case IN_PROGRESS -> COMPLETED;
                case COMPLETED -> null;
            };
        }
    }
}
