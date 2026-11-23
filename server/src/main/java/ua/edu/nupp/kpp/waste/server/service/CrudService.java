package ua.edu.nupp.kpp.waste.server.service;

import ua.edu.nupp.kpp.waste.server.dao.Repository;
import java.util.List;
import java.util.logging.Logger;

public abstract class CrudService<T> {
    protected final Repository<T> repository;
    /** The four services share this monitor within one server instance. */
    protected final Object writeLock;
    protected final Logger log = Logger.getLogger(getClass().getName());

    protected CrudService(Repository<T> repository, Object writeLock) {
        this.repository = repository;
        this.writeLock = writeLock;
    }

    protected abstract T normalize(T value, T existing);

    public List<T> list() { return repository.findAll(); }

    public T get(long id) {
        Validation.id(id, "ID");
        return repository.findById(id).orElseThrow(ApiException::notFound);
    }

    public T create(T value) {
        requireValue(value);
        synchronized (writeLock) {
            long id = repository.insert(normalize(value, null));
            log.info(() -> "Створено запис id=" + id);
            return get(id);
        }
    }

    public T update(long id, T value) {
        requireValue(value);
        synchronized (writeLock) {
            T existing = get(id);
            if (!repository.update(id, normalize(value, existing))) throw ApiException.notFound();
            log.info(() -> "Оновлено запис id=" + id);
            return get(id);
        }
    }

    public void delete(long id) {
        Validation.id(id, "ID");
        synchronized (writeLock) {
            if (!repository.delete(id)) throw ApiException.notFound();
            log.info(() -> "Видалено запис id=" + id);
        }
    }

    private static void requireValue(Object value) {
        if (value == null) throw ApiException.badRequest("Потрібне тіло запиту");
    }
}
