package ua.edu.nupp.kpp.waste.server.service;

import ua.edu.nupp.kpp.waste.server.dao.Repository;
import ua.edu.nupp.kpp.waste.server.dao.TripRepository;
import ua.edu.nupp.kpp.waste.server.model.Request;
import ua.edu.nupp.kpp.waste.server.model.Trip;
import ua.edu.nupp.kpp.waste.server.model.Vehicle;
import java.util.Objects;

public final class TripService extends CrudService<Trip> {
    private final TripRepository trips;
    private final Repository<Request> requests;
    private final Repository<Vehicle> vehicles;

    public TripService(TripRepository trips, Repository<Request> requests, Repository<Vehicle> vehicles, Object writeLock) {
        super(trips, writeLock);
        this.trips = trips;
        this.requests = requests;
        this.vehicles = vehicles;
    }

    @Override protected Trip normalize(Trip value, Trip existing) {
        long requestId = Validation.id(value.requestId(), "Заявка");
        long vehicleId = Validation.id(value.vehicleId(), "Транспорт");
        if (existing != null && !Objects.equals(existing.requestId(), requestId)) {
            requireEditable(existing.requestId());
        }
        requireEditable(requestId);
        if (vehicles.findById(vehicleId).isEmpty()) throw ApiException.badRequest("Транспорт не знайдено");
        Trip linked = trips.findByRequest(requestId).orElse(null);
        if (linked != null && (existing == null || !Objects.equals(linked.id(), existing.id()))) {
            throw ApiException.conflict("У цієї заявки вже є виїзд");
        }
        return new Trip(existing == null ? null : existing.id(), requestId, vehicleId,
                Validation.date(value.scheduledDate()), Validation.optional(value.notes(), "Примітка", 240));
    }

    @Override public void delete(long id) {
        synchronized (writeLock) {
            requireEditable(get(id).requestId());
            super.delete(id);
        }
    }

    private void requireEditable(long requestId) {
        Request request = requests.findById(requestId)
                .orElseThrow(() -> ApiException.badRequest("Заявку не знайдено"));
        if (request.status().locksTrip()) {
            throw ApiException.conflict("Виїзд запланованої заявки або заявки у виконанні заблоковано");
        }
    }
}
