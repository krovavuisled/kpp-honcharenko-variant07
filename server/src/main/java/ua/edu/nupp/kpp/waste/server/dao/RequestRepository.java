package ua.edu.nupp.kpp.waste.server.dao;

import ua.edu.nupp.kpp.waste.server.model.Request;
import ua.edu.nupp.kpp.waste.server.model.RequestStatus;

public interface RequestRepository extends Repository<Request> {
    boolean changeStatus(long id, RequestStatus previous, RequestStatus next);
}
