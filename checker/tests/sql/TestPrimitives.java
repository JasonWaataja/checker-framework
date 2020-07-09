import java.sql.*;

public class TestPrimitives {
    Statement statement;

    public void testPrimitives() throws SQLException {
        statement.execute("" + 5);
        statement.execute("" + 5L);
        statement.execute("" + 5.0);
        statement.execute("" + 5.0f);
        statement.execute("" + null);
        statement.execute("" + true);
    }
}
