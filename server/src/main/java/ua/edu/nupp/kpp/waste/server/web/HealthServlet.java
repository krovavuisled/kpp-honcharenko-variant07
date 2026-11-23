package ua.edu.nupp.kpp.waste.server.web;

import ua.edu.nupp.kpp.waste.server.dao.Db;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/api/health")
public final class HealthServlet extends HttpServlet {
    private static final Logger LOG = Logger.getLogger(HealthServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        boolean ready = false;
        try (Connection connection = Db.open();
             PreparedStatement statement = connection.prepareStatement("SELECT 1");
             ResultSet result = statement.executeQuery()) {
            ready = result.next();
        } catch (SQLException ex) {
            LOG.log(Level.WARNING, "MySQL недоступний. SQLState={0}, код={1}",
                    new Object[] {ex.getSQLState(), ex.getErrorCode()});
        }
        if (ready) {
            CrudServlet.write(response, 200, Map.of("status", "UP", "database", "UP", "variant", 7));
        } else {
            CrudServlet.write(response, 503, Map.of("status", "DOWN", "database", "DOWN", "variant", 7,
                    "message", "База даних недоступна. Перевірте MySQL та конфігурацію сервера"));
        }
    }
}
