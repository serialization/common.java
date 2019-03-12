package ogss.common.java.internal.nodes;

import java.util.ArrayList;
import java.util.Set;

import ogss.common.java.api.Attribute;
import ogss.common.java.api.Edge;
import ogss.common.java.api.Graph;
import ogss.common.java.api.Node;

/**
 * A node wrapping a Set. This is actually quite tricky in Java, because we cannot reconstruct our base type.
 * 
 * @author Timm Felden
 */
public final class SetNode extends Node {
    private final Set<?> repr;

    public SetNode(Graph owner, Set<?> repr) {
        super(owner);
        this.repr = repr;
    }

    @Override
    public void resetEdges() {
        edges = new ArrayList<>();
        for (Object x : repr) {
            // detect attributes
            if (x != null && (x instanceof Number || x instanceof Boolean)) {
                edges.clear();
                return;
            }
            edges.add(new Edge(this, owner.getNodeFor(x), false, x.toString()));
        }
    }

    @Override
    public void resetAttributes() {
        attributes = new ArrayList<>();
        for (Object x : repr) {
            // detect edges
            if (!(x instanceof Number || x instanceof Boolean)) {
                attributes.clear();
                return;
            }
            attributes.add(new Attribute(x.toString(), false, x));
        }
    }

    @Override
    public Object repr() {
        return repr;
    }

    @Override
    public String toString() {
        return "Set#" + System.identityHashCode(repr);
    }

}
