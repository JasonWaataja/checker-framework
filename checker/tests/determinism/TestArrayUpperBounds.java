package determinism;

import java.util.*;
import org.checkerframework.checker.determinism.qual.*;

// @skip-test
class TestArrayUpperBounds {
    public static <T> T[] newArray() {
        T[] arr = (T[]) new Object[0];
        return arr;
    }
}
