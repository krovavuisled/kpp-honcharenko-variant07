package ua.edu.nupp.kpp.waste.server.service;

import ua.edu.nupp.kpp.waste.server.dao.Repository;
import ua.edu.nupp.kpp.waste.server.model.Site;

public final class SiteService extends CrudService<Site> {
    public SiteService(Repository<Site> repository, Object writeLock) { super(repository, writeLock); }

    @Override protected Site normalize(Site value, Site existing) {
        return new Site(existing == null ? null : existing.id(),
                Validation.required(value.code(), "Код майданчика", 24),
                Validation.required(value.name(), "Назва", 100),
                Validation.required(value.address(), "Адреса", 180),
                Validation.integer(value.capacityContainers(), "Кількість контейнерів", 1, 500));
    }
}
