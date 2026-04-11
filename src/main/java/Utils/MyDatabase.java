package Utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class MyDatabase {
    private static final String URL = "jdbc:mysql://localhost:3306/app?serverTimezone=UTC";
    private static final String USER = "app";
    private static final String PASSWORD = "!ChangeMe!";

    private static MyDatabase instance;
    private final Connection connection;

    private MyDatabase() {
        try {
            connection = DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (SQLException e) {
            throw new RuntimeException("Database connection failed: " + e.getMessage(), e);
        }
    }

    public static MyDatabase getInstance() {
        if (instance == null) {
            instance = new MyDatabase();
        }
        return instance;
    }

    public Connection getConnection() {
        return connection;
    }
}
