import testlib.wholeprograminference.qual.*;

public class GenericClassWithInner<T> {
    public class InnerClass {
        // Add error to force generation of stub file.
        // :: error: (assignment.type.incompatible)
        Object o = (@Top Object) null;
    }
}
