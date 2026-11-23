package ua.edu.nupp.kpp.waste.server.service;

import ua.edu.nupp.kpp.waste.server.dao.Repository;
import ua.edu.nupp.kpp.waste.server.model.Vehicle;

public final class VehicleService extends CrudService<Vehicle> {
    public VehicleService(Repository<Vehicle> repository, Object writeLock) { super(repository, writeLock); }

    @Override protected Vehicle normalize(Vehicle value, Vehicle existing) {
        if (value.active() == null) throw ApiException.badRequest("Укажіть, чи доступний транспортний засіб");
        return new Vehicle(existing == null ? null : existing.id(),
                Validation.required(value.plate(), "Реєстраційний номер", 24),
                Validation.required(value.model(), "Модель", 100),
                Validation.integer(value.capacityKg(), "Вантажопідйомність, кг", 1, 50000),
                value.active());
    }
}
