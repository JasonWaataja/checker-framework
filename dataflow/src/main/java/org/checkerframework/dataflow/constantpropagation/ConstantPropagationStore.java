package org.checkerframework.dataflow.constantpropagation;

import java.util.HashMap;
import java.util.Map;
import org.checkerframework.checker.determinism.qual.NonDet;
import org.checkerframework.checker.determinism.qual.OrderNonDet;
import org.checkerframework.checker.determinism.qual.PolyDet;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.analysis.FlowExpressions;
import org.checkerframework.dataflow.analysis.Store;
import org.checkerframework.dataflow.cfg.CFGVisualizer;
import org.checkerframework.dataflow.cfg.node.IntegerLiteralNode;
import org.checkerframework.dataflow.cfg.node.LocalVariableNode;
import org.checkerframework.dataflow.cfg.node.Node;

public class ConstantPropagationStore implements Store<ConstantPropagationStore> {

    /** Information about variables gathered so far. */
    @OrderNonDet Map<Node, Constant> contents;

    public ConstantPropagationStore() {
        contents = new HashMap<>();
    }

    protected ConstantPropagationStore(@OrderNonDet Map<Node, Constant> contents) {
        this.contents = contents;
    }

    public Constant getInformation(Node n) {
        if (contents.containsKey(n)) {
            return contents.get(n);
        }
        return new Constant(Constant.Type.TOP);
    }

    public void mergeInformation(Node n, Constant val) {
        Constant value;
        if (contents.containsKey(n)) {
            value = val.leastUpperBound(contents.get(n));
        } else {
            value = val;
        }
        // TODO: remove (only two nodes supported atm)
        assert n instanceof IntegerLiteralNode || n instanceof LocalVariableNode;
        contents.put(n, value);
    }

    public void setInformation(Node n, Constant val) {
        // TODO: remove (only two nodes supported atm)
        assert n instanceof IntegerLiteralNode || n instanceof LocalVariableNode;
        contents.put(n, val);
    }

    @Override
    @SuppressWarnings(
            "determinism") // valid rule relaxation: copy clearly preserves determinism type
    public @PolyDet ConstantPropagationStore copy(@PolyDet ConstantPropagationStore this) {
        return new ConstantPropagationStore(new HashMap<>(contents));
    }

    @Override
    @SuppressWarnings("determinism") // process order insensitive
    public ConstantPropagationStore leastUpperBound(ConstantPropagationStore other) {
        Map<Node, Constant> newContents = new HashMap<>();

        // go through all of the information of the other class
        for (Map.Entry<Node, Constant> e : other.contents.entrySet()) {
            Node n = e.getKey();
            Constant otherVal = e.getValue();
            if (contents.containsKey(n)) {
                // merge if both contain information about a variable
                newContents.put(n, otherVal.leastUpperBound(contents.get(n)));
            } else {
                // add new information
                newContents.put(n, otherVal);
            }
        }

        for (Map.Entry<Node, Constant> e : contents.entrySet()) {
            Node n = e.getKey();
            Constant thisVal = e.getValue();
            if (!other.contents.containsKey(n)) {
                // add new information
                newContents.put(n, thisVal);
            }
        }

        return new ConstantPropagationStore(newContents);
    }

    @Override
    public ConstantPropagationStore widenedUpperBound(ConstantPropagationStore previous) {
        return leastUpperBound(previous);
    }

    @Override
    public @PolyDet boolean equals(
            @PolyDet ConstantPropagationStore this, @PolyDet @Nullable Object o) {
        if (o == null) {
            return false;
        }
        if (!(o instanceof ConstantPropagationStore)) {
            return false;
        }
        ConstantPropagationStore other = (ConstantPropagationStore) o;
        // go through all of the information of the other object
        for (Map.Entry<Node, Constant> e : other.contents.entrySet()) {
            Node n = e.getKey();
            Constant otherVal = e.getValue();
            if (otherVal.isBottom()) {
                continue; // no information
            }
            if (contents.containsKey(n)) {
                if (!otherVal.equals(contents.get(n))) {
                    return false;
                }
            } else {
                return false;
            }
        }
        // go through all of the information of the this object
        for (Map.Entry<Node, Constant> e : contents.entrySet()) {
            Node n = e.getKey();
            Constant thisVal = e.getValue();
            if (thisVal.isBottom()) {
                continue; // no information
            }
            if (other.contents.containsKey(n)) {
                continue;
            } else {
                return false;
            }
        }
        return true;
    }

    @Override
    @SuppressWarnings("determinism") // calling method on external class requires @Det: Integer
    public @NonDet int hashCode() {
        int s = 0;
        for (Map.Entry<Node, Constant> e : contents.entrySet()) {
            if (!e.getValue().isBottom()) {
                s += e.hashCode();
            }
        }
        return s;
    }

    @Override
    @SuppressWarnings("determinism") // non-determinism reflected in return type
    public @NonDet String toString(@PolyDet ConstantPropagationStore this) {
        // only output local variable information
        Map<Node, Constant> smallerContents = new HashMap<>();
        for (Map.Entry<Node, Constant> e : contents.entrySet()) {
            if (e.getKey() instanceof LocalVariableNode) {
                smallerContents.put(e.getKey(), e.getValue());
            }
        }
        return smallerContents.toString();
    }

    @Override
    public boolean canAlias(FlowExpressions.Receiver a, FlowExpressions.Receiver b) {
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code value} is {@code null} because {@link ConstantPropagationStore} doesn't support
     * visualization.
     */
    @Override
    @SuppressWarnings("nullness")
    public @NonDet String visualize(CFGVisualizer<?, ConstantPropagationStore, ?> viz) {
        return viz.visualizeStoreKeyVal("constant propagation", null);
    }
}
