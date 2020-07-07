// test file for https://github.com/typetools/checker-framework/issues/3438

import testlib.wholeprograminference.qual.Top;

public class GenericClassWithInner<T> {
    public class InnerClass {
        // Add error to force generation of stub file.
        // :: error: (assignment.type.incompatible)
        Object o = (@Top Object) null;
    }
}
