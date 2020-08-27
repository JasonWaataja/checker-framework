package org.checkerframework.dataflow.cfg.node;

import com.sun.source.tree.Tree;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import javax.lang.model.type.TypeMirror;
import org.checkerframework.checker.determinism.qual.NonDet;
import org.checkerframework.checker.determinism.qual.PolyDet;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.javacutil.TypesUtils;

/**
 * A node for the widening primitive conversion operation. See JLS 5.1.2 for the definition of
 * widening primitive conversion.
 *
 * <p>A {@link WideningConversionNode} does not correspond to any tree node in the parsed AST. It is
 * introduced when a value of some primitive type appears in a context that requires a different
 * primitive with more bits of precision.
 */
public class WideningConversionNode extends Node {

    protected final Tree tree;
    protected final Node operand;

    public WideningConversionNode(Tree tree, Node operand, TypeMirror type) {
        super(type);
        assert TypesUtils.isPrimitive(type) : "non-primitive type in widening conversion";
        this.tree = tree;
        this.operand = operand;
    }

    public @PolyDet Node getOperand(@PolyDet WideningConversionNode this) {
        return operand;
    }

    @Override
    public @PolyDet Tree getTree(@PolyDet WideningConversionNode this) {
        return tree;
    }

    @Override
    public <R, P> R accept(NodeVisitor<R, P> visitor, P p) {
        return visitor.visitWideningConversion(this, p);
    }

    @Override
    public @PolyDet String toString(@PolyDet WideningConversionNode this) {
        return "WideningConversion(" + getOperand() + ", " + type + ")";
    }

    @Override
    public @PolyDet boolean equals(
            @PolyDet WideningConversionNode this, @PolyDet @Nullable Object obj) {
        if (!(obj instanceof WideningConversionNode)) {
            return false;
        }
        WideningConversionNode other = (WideningConversionNode) obj;
        return getOperand().equals(other.getOperand())
                && TypesUtils.areSamePrimitiveTypes(getType(), other.getType());
    }

    @Override
    public @NonDet int hashCode(@PolyDet WideningConversionNode this) {
        return Objects.hash(WideningConversionNode.class, getOperand());
    }

    @Override
    public Collection<Node> getOperands() {
        return Collections.singletonList(getOperand());
    }
}
