package ua.edu.nupp.kpp.waste.server.dao;

import java.util.List;
import java.util.Optional;

public interface Repository<T> {
    List<T> findAll();
    Optional<T> findById(long id);
    long insert(T value);
    boolean update(long id, T value);
    boolean delete(long id);
}
