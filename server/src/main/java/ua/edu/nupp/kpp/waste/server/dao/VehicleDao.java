package ua.edu.nupp.kpp.waste.server.dao;

import ua.edu.nupp.kpp.waste.server.model.Vehicle;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class VehicleDao extends JdbcRepository<Vehicle> {
    public VehicleDao() { super("vehicle"); }

    @Override protected Vehicle read(ResultSet rows) throws SQLException {
        return new Vehicle(rows.getLong("id"), rows.getString("plate"), rows.getString("model"),
                rows.getInt("capacity_kg"), rows.getBoolean("active"));
    }

    @Override protected String insertSql() {
        return "INSERT INTO vehicle (plate, model, capacity_kg, active) VALUES (?, ?, ?, ?)";
    }

    @Override protected String updateSql() {
        return "UPDATE vehicle SET plate = ?, model = ?, capacity_kg = ?, active = ? WHERE id = ?";
    }

    @Override protected int bind(PreparedStatement statement, Vehicle value) throws SQLException {
        statement.setString(1, value.plate());
        statement.setString(2, value.model());
        statement.setInt(3, value.capacityKg());
        statement.setBoolean(4, value.active());
        return 5;
    }
}
