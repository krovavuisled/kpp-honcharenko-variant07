package ua.edu.nupp.kpp.waste.server.dao;

import java.sql.SQLException;

public final class DataAccessException extends RuntimeException {
    private final boolean conflict;

    public DataAccessException(SQLException cause) {
        super("Database operation failed", cause);
        conflict = cause.getSQLState() != null && cause.getSQLState().startsWith("23");
    }

    public boolean conflict() { return conflict; }
}
