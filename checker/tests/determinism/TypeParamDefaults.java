import java.util.List;
import org.checkerframework.checker.determinism.qual.PolyDet;

public class TypeParamDefaults {
    void test(List<@PolyDet String> lst) {
        @PolyDet List<@PolyDet String> lst1 = lst;
    }

    void test1(List<@PolyDet List<@PolyDet String>> lst) {
        @PolyDet List<@PolyDet List<@PolyDet String>> lst1 = lst;
    }
}
