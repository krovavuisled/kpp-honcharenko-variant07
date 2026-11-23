package ua.edu.nupp.kpp.waste.server.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class Db {
    private Db() { }

    public static Connection open() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ex) {
            throw new SQLException("MySQL JDBC driver unavailable", ex);
        }
        return DriverManager.getConnection(required("DB_URL"), required("DB_USER"), required("DB_PASSWORD"));
    }

    private static String required(String name) throws SQLException {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new SQLException("Missing database configuration: " + name);
        }
        return value;
    }
}
