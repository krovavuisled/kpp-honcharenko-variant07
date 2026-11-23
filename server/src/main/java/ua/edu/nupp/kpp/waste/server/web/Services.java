package ua.edu.nupp.kpp.waste.server.web;

import ua.edu.nupp.kpp.waste.server.dao.RequestDao;
import ua.edu.nupp.kpp.waste.server.dao.SiteDao;
import ua.edu.nupp.kpp.waste.server.dao.TripDao;
import ua.edu.nupp.kpp.waste.server.dao.VehicleDao;
import ua.edu.nupp.kpp.waste.server.service.RequestService;
import ua.edu.nupp.kpp.waste.server.service.SiteService;
import ua.edu.nupp.kpp.waste.server.service.TripService;
import ua.edu.nupp.kpp.waste.server.service.VehicleService;

final class Services {
    private static final Object WRITE_LOCK = new Object();
    private static final SiteDao SITES = new SiteDao();
    private static final VehicleDao VEHICLES = new VehicleDao();
    private static final RequestDao REQUESTS = new RequestDao();
    private static final TripDao TRIPS = new TripDao();

    static final SiteService SITE = new SiteService(SITES, WRITE_LOCK);
    static final VehicleService VEHICLE = new VehicleService(VEHICLES, WRITE_LOCK);
    static final RequestService REQUEST = new RequestService(REQUESTS, SITES, TRIPS, VEHICLES, WRITE_LOCK);
    static final TripService TRIP = new TripService(TRIPS, REQUESTS, VEHICLES, WRITE_LOCK);

    private Services() { }
}
