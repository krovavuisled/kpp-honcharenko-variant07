package ua.edu.nupp.kpp.waste.server.web;

import ua.edu.nupp.kpp.waste.server.model.Request;
import ua.edu.nupp.kpp.waste.server.model.RequestStatus;
import ua.edu.nupp.kpp.waste.server.service.ApiException;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet("/api/requests/*")
public final class RequestServlet extends CrudServlet<Request> {
    private static final String STATUS_SUFFIX = "/status";

    public RequestServlet() { super(Services.REQUEST, Request.class); }

    public record StatusInput(RequestStatus status) { }

    @Override
    void route(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getPathInfo();
        if (path == null || !path.endsWith(STATUS_SUFFIX)) {
            super.route(request, response);
            return;
        }
        long id = parseId(path.substring(0, path.length() - STATUS_SUFFIX.length()));
        response.setHeader("Allow", "PATCH");
        if (!"PATCH".equals(request.getMethod())) throw new ApiException(405, "Для зміни стану використовуйте PATCH");
        StatusInput input = read(request, StatusInput.class);
        write(response, 200, Services.REQUEST.transition(id, input.status()));
    }
}
