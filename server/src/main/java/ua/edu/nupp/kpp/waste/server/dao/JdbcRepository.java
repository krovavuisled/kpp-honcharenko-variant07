package ua.edu.nupp.kpp.waste.server.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Table and SQL arguments originate in the concrete DAO classes. Values are bound separately. */
public abstract class JdbcRepository<T> implements Repository<T> {
    private final String table;

    protected JdbcRepository(String table) { this.table = table; }

    protected abstract T read(ResultSet rows) throws SQLException;
    protected abstract String insertSql();
    protected abstract String updateSql();
    /** Binds fields and returns the index for the following parameter. */
    protected abstract int bind(PreparedStatement statement, T value) throws SQLException;

    @Override
    public List<T> findAll() {
        String sql = "SELECT * FROM " + table + " ORDER BY id";
        try (Connection connection = Db.open();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            List<T> values = new ArrayList<>();
            while (rows.next()) values.add(read(rows));
            return values;
        } catch (SQLException ex) {
            throw new DataAccessException(ex);
        }
    }

    @Override
    public Optional<T> findById(long id) {
        return findOne("SELECT * FROM " + table + " WHERE id = ?", id);
    }

    protected Optional<T> findOne(String sql, long key) {
        try (Connection connection = Db.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, key);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(read(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new DataAccessException(ex);
        }
    }

    @Override
    public long insert(T value) {
        try (Connection connection = Db.open();
             PreparedStatement statement = connection.prepareStatement(insertSql(), Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, value);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Generated ID missing");
                return keys.getLong(1);
            }
        } catch (SQLException ex) {
            throw new DataAccessException(ex);
        }
    }

    @Override
    public boolean update(long id, T value) {
        try (Connection connection = Db.open();
             PreparedStatement statement = connection.prepareStatement(updateSql())) {
            int next = bind(statement, value);
            statement.setLong(next, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new DataAccessException(ex);
        }
    }

    @Override
    public boolean delete(long id) {
        try (Connection connection = Db.open();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table + " WHERE id = ?")) {
            statement.setLong(1, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new DataAccessException(ex);
        }
    }
}
