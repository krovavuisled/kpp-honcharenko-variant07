package ua.edu.nupp.kpp.waste.server.dao;

import ua.edu.nupp.kpp.waste.server.model.Request;
import ua.edu.nupp.kpp.waste.server.model.RequestStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class RequestDao extends JdbcRepository<Request> implements RequestRepository {
    public RequestDao() { super("pickup_request"); }

    @Override protected Request read(ResultSet rows) throws SQLException {
        return new Request(rows.getLong("id"), rows.getLong("site_id"), rows.getString("title"),
                rows.getBigDecimal("fill_level"), rows.getBigDecimal("sanitary_risk"),
                rows.getInt("overdue_hours"), rows.getBigDecimal("priority"),
                RequestStatus.valueOf(rows.getString("status")));
    }

    @Override protected String insertSql() {
        return "INSERT INTO pickup_request (site_id, title, fill_level, sanitary_risk, overdue_hours, priority) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
    }

    @Override protected String updateSql() {
        // Status is writable only through changeStatus.
        return "UPDATE pickup_request SET site_id = ?, title = ?, fill_level = ?, sanitary_risk = ?, "
                + "overdue_hours = ?, priority = ? WHERE id = ?";
    }

    @Override protected int bind(PreparedStatement statement, Request value) throws SQLException {
        statement.setLong(1, value.siteId());
        statement.setString(2, value.title());
        statement.setBigDecimal(3, value.fillLevel());
        statement.setBigDecimal(4, value.sanitaryRisk());
        statement.setInt(5, value.overdueHours());
        statement.setBigDecimal(6, value.priority());
        return 7;
    }

    @Override
    public boolean changeStatus(long id, RequestStatus previous, RequestStatus next) {
        String sql = "UPDATE pickup_request SET status = ? WHERE id = ? AND status = ?";
        try (Connection connection = Db.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, next.name());
            statement.setLong(2, id);
            statement.setString(3, previous.name());
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new DataAccessException(ex);
        }
    }
}
