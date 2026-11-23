package ua.edu.nupp.kpp.waste.server.model;

public enum RequestStatus {
    CREATED, PLANNED, IN_PROGRESS, COMPLETED;

    public RequestStatus next() {
        return switch (this) {
            case CREATED -> PLANNED;
            case PLANNED -> IN_PROGRESS;
            case IN_PROGRESS -> COMPLETED;
            case COMPLETED -> null;
        };
    }

    public boolean locksTrip() {
        return this == PLANNED || this == IN_PROGRESS;
    }
}
