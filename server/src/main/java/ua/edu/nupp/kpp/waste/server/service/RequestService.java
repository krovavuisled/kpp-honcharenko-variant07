package ua.edu.nupp.kpp.waste.server.service;

import ua.edu.nupp.kpp.waste.server.dao.Repository;
import ua.edu.nupp.kpp.waste.server.dao.RequestRepository;
import ua.edu.nupp.kpp.waste.server.dao.TripRepository;
import ua.edu.nupp.kpp.waste.server.model.Request;
import ua.edu.nupp.kpp.waste.server.model.RequestStatus;
import ua.edu.nupp.kpp.waste.server.model.Site;
import ua.edu.nupp.kpp.waste.server.model.Trip;
import ua.edu.nupp.kpp.waste.server.model.Vehicle;

import java.math.BigDecimal;

public final class RequestService extends CrudService<Request> {
    private final RequestRepository requests;
    private final Repository<Site> sites;
    private final TripRepository trips;
    private final Repository<Vehicle> vehicles;

    public RequestService(RequestRepository requests, Repository<Site> sites, TripRepository trips,
                          Repository<Vehicle> vehicles, Object writeLock) {
        super(requests, writeLock);
        this.requests = requests;
        this.sites = sites;
        this.trips = trips;
        this.vehicles = vehicles;
    }

    @Override protected Request normalize(Request value, Request existing) {
        RequestStatus status = existing == null ? RequestStatus.CREATED : existing.status();
        if (value.status() != null && value.status() != status) {
            throw ApiException.conflict("Стан заявки змінюється окремою операцією");
        }
        long siteId = Validation.id(value.siteId(), "Майданчик");
        if (sites.findById(siteId).isEmpty()) throw ApiException.badRequest("Майданчик не знайдено");
        String title = Validation.required(value.title(), "Назва заявки", 140);
        BigDecimal fill = Validation.score(value.fillLevel(), "Рівень заповнення");
        BigDecimal risk = Validation.score(value.sanitaryRisk(), "Санітарний ризик");
        int overdue = Validation.integer(value.overdueHours(), "Години прострочення", 0, 8760);
        return new Request(existing == null ? null : existing.id(), siteId, title, fill, risk, overdue,
                PriorityCalculator.calculate(fill, risk, overdue), status);
    }

    public Request transition(long id, RequestStatus next) {
        if (next == null) throw ApiException.badRequest("Укажіть наступний стан заявки");
        synchronized (writeLock) {
            Request current = get(id);
            if (current.status().next() != next) {
                throw ApiException.conflict("Недопустимий перехід стану: " + current.status() + " → " + next);
            }
            if (next == RequestStatus.PLANNED) {
                Trip trip = trips.findByRequest(id)
                        .orElseThrow(() -> ApiException.conflict("Спочатку створіть виїзд для цієї заявки"));
                Vehicle vehicle = vehicles.findById(trip.vehicleId())
                        .orElseThrow(() -> ApiException.conflict("Транспорт для виїзду не знайдено"));
                if (!Boolean.TRUE.equals(vehicle.active())) {
                    throw ApiException.conflict("Для планування потрібен доступний транспортний засіб");
                }
            }
            if (!requests.changeStatus(id, current.status(), next)) {
                throw ApiException.conflict("Заявку вже змінено. Оновіть список і повторіть операцію");
            }
            log.info(() -> "Заявка id=" + id + ": " + current.status() + " → " + next);
            return get(id);
        }
    }
}
