import java.sql.SQLException;
import java.sql.Statement;
import org.checkerframework.checker.sql.qual.*;

public class StringConstants {
    public static final String SQL_CONSTANT = "";
    public static final String WITH_CONCATENATION = "a" + "b";

    Statement statement = null;

    public void testStringConstant() throws SQLException {
        statement.execute(SQL_CONSTANT);
    }

    public void testStringConstantWithConcatenation() throws SQLException {
        statement.execute(WITH_CONCATENATION);
    }
}
