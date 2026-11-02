import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

final class VerificationDatabase {
    private VerificationDatabase() {
    }

    static Connection connect() throws SQLException {
        String url = required("REDWEB_DB_URL");
        String username = required("REDWEB_DB_USERNAME");
        String password = required("REDWEB_DB_PASSWORD");
        return DriverManager.getConnection(url, username, password);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be supplied to the verification process");
        }
        return value;
    }
}
