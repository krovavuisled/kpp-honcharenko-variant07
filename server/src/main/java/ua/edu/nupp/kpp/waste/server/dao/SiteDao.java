package ua.edu.nupp.kpp.waste.server.dao;

import ua.edu.nupp.kpp.waste.server.model.Site;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class SiteDao extends JdbcRepository<Site> {
    public SiteDao() { super("container_site"); }

    @Override protected Site read(ResultSet rows) throws SQLException {
        return new Site(rows.getLong("id"), rows.getString("code"), rows.getString("name"),
                rows.getString("address"), rows.getInt("capacity_containers"));
    }

    @Override protected String insertSql() {
        return "INSERT INTO container_site (code, name, address, capacity_containers) VALUES (?, ?, ?, ?)";
    }

    @Override protected String updateSql() {
        return "UPDATE container_site SET code = ?, name = ?, address = ?, capacity_containers = ? WHERE id = ?";
    }

    @Override protected int bind(PreparedStatement statement, Site value) throws SQLException {
        statement.setString(1, value.code());
        statement.setString(2, value.name());
        statement.setString(3, value.address());
        statement.setInt(4, value.capacityContainers());
        return 5;
    }
}
