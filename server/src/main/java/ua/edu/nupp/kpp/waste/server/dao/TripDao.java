package ua.edu.nupp.kpp.waste.server.dao;

import ua.edu.nupp.kpp.waste.server.model.Trip;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public final class TripDao extends JdbcRepository<Trip> implements TripRepository {
    public TripDao() { super("collection_trip"); }

    @Override protected Trip read(ResultSet rows) throws SQLException {
        return new Trip(rows.getLong("id"), rows.getLong("request_id"), rows.getLong("vehicle_id"),
                rows.getDate("scheduled_date").toLocalDate().toString(), rows.getString("notes"));
    }

    @Override protected String insertSql() {
        return "INSERT INTO collection_trip (request_id, vehicle_id, scheduled_date, notes) VALUES (?, ?, ?, ?)";
    }

    @Override protected String updateSql() {
        return "UPDATE collection_trip SET request_id = ?, vehicle_id = ?, scheduled_date = ?, notes = ? WHERE id = ?";
    }

    @Override protected int bind(PreparedStatement statement, Trip value) throws SQLException {
        statement.setLong(1, value.requestId());
        statement.setLong(2, value.vehicleId());
        statement.setDate(3, Date.valueOf(value.scheduledDate()));
        statement.setString(4, value.notes());
        return 5;
    }

    @Override
    public Optional<Trip> findByRequest(long requestId) {
        return findOne("SELECT * FROM collection_trip WHERE request_id = ?", requestId);
    }
}
