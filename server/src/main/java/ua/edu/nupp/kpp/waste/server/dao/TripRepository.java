package ua.edu.nupp.kpp.waste.server.dao;

import ua.edu.nupp.kpp.waste.server.model.Trip;
import java.util.Optional;

public interface TripRepository extends Repository<Trip> {
    Optional<Trip> findByRequest(long requestId);
}
