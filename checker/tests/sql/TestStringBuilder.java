import java.sql.*;
import org.checkerframework.checker.sql.qual.*;

public class TestStringBuilder {
    Statement statement;

    void testSafeSql() throws SQLException {
        @NotDangerousSql StringBuilder builder = new StringBuilder();
        builder.append("abc");
        statement.execute(builder.toString());
    }

    void testUnsafeBuilderAppend(@MaybeDangerousSql String unsafe) throws SQLException {
        @NotDangerousSql StringBuilder builder = new StringBuilder();
        // :: error: (argument.type.incompatible)
        builder.append(unsafe);
        statement.execute(builder.toString());
    }

    void testUnsafeBuilderUse(@MaybeDangerousSql String unsafe) throws SQLException {
        StringBuilder builder = new @MaybeDangerousSql StringBuilder();
        builder.append(unsafe);
        // :: error: (argument.type.incompatible)
        statement.execute(builder.toString());
    }
}
