import org.checkerframework.checker.sql.qual.NotDangerousSql;

class SimpleSql {

    void execute(@NotDangerousSql String s) {}

    void unsafe(String s) {}

    void stringLiteral() {
        execute("ldskjfldj");
        unsafe("lksjdflkjdf");
    }

    void stringRef(String ref) {
        // :: error: (argument.type.incompatible)
        execute(ref);
        unsafe(ref);
    }

    void ununsafeRef(@NotDangerousSql String ref) {
        execute(ref);
        unsafe(ref);
    }

    void concatenation(@NotDangerousSql String s1, String s2) {
        execute(s1 + s1);
        execute(s1 += s1);
        execute(s1 + "m");
        // :: error: (argument.type.incompatible)
        execute(s1 + s2);

        // :: error: (argument.type.incompatible)
        execute(s2 + s1);
        // :: error: (argument.type.incompatible)
        execute(s2 + "m");
        // :: error: (argument.type.incompatible)
        execute(s2 + s2);

        unsafe(s1 + s1);
        unsafe(s1 + "m");
        unsafe(s1 + s2);

        unsafe(s2 + s1);
        unsafe(s2 + "m");
        unsafe(s2 + s2);
    }
}
