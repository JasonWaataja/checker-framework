import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.checkerframework.checker.sql.qual.*;

public class Jdbc {
    Statement statement;
    Connection conn;

    void testStringLiteral() throws SQLException {
        statement.execute("");
    }

    void testVulnerableStatement(String unsafe) throws SQLException {
        // :: error: (argument.type.incompatible)
        statement.execute(unsafe);
        // :: error: (argument.type.incompatible)
        statement.executeQuery(unsafe);
        // :: error: (argument.type.incompatible)
        statement.executeUpdate(unsafe);
    }

    void testAddBatch(String unsafe) throws SQLException {
        // :: error: (argument.type.incompatible)
        statement.addBatch(unsafe);
    }

    void testVulnerablePreparedStatement(String unsafe) throws SQLException {
        // :: error: (argument.type.incompatible)
        conn.prepareStatement(unsafe);
        // :: error: (argument.type.incompatible)
        conn.prepareCall(unsafe);
    }
}
