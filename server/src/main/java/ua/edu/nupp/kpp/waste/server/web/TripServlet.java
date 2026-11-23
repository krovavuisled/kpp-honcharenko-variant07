package ua.edu.nupp.kpp.waste.server.web;

import ua.edu.nupp.kpp.waste.server.model.Trip;
import javax.servlet.annotation.WebServlet;

@WebServlet("/api/trips/*")
public final class TripServlet extends CrudServlet<Trip> {
    public TripServlet() { super(Services.TRIP, Trip.class); }
}
