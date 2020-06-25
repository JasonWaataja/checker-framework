package tests;

import java.io.File;
import java.util.List;
import org.checkerframework.checker.sql.SqlChecker;
import org.checkerframework.framework.test.CheckerFrameworkPerDirectoryTest;
import org.junit.runners.Parameterized.Parameters;

public class SqlTest extends CheckerFrameworkPerDirectoryTest {

    /**
     * Create a SqlTest.
     *
     * @param testFiles the files containing test code, which will be type-checked
     */
    public SqlTest(List<File> testFiles) {
        super(testFiles, SqlChecker.class, "sql", "-Anomsgtext");
    }

    @Parameters
    public static String[] getTestDirs() {
        return new String[] {"sql", "all-systems"};
    }
}
