package utils.manager;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class ConnectionManager {
    private ConnectionManager() {
    }

    private static final String USERNAME_KEY = ".username";
    private static final String PASSWORD_KEY = ".password";
    private static final String URL = ".url";

    public static Connection open(String key) {
        try {
            return DriverManager.getConnection(
                    PropertiesUtil.getProperty(key + URL),
                    PropertiesUtil.getProperty(key + USERNAME_KEY),
                    PropertiesUtil.getProperty(key + PASSWORD_KEY));
        } catch (SQLException e) {
            throw new RuntimeException("Connection error");
        }
    }
}
