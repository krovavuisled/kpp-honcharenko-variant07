package ua.edu.nupp.kpp.waste.server.web;

import ua.edu.nupp.kpp.waste.server.model.Vehicle;
import javax.servlet.annotation.WebServlet;

@WebServlet("/api/vehicles/*")
public final class VehicleServlet extends CrudServlet<Vehicle> {
    public VehicleServlet() { super(Services.VEHICLE, Vehicle.class); }
}
