package ogss.common.java.internal.nodes;

import java.util.ArrayList;
import java.util.Collection;

import ogss.common.java.api.Attribute;
import ogss.common.java.api.Edge;
import ogss.common.java.api.Graph;
import ogss.common.java.api.Node;

/**
 * A node wrapping a Sequence. This is actually quite tricky in Java, because we cannot reconstruct our base type.
 * 
 * @author Timm Felden
 */
public final class SeqNode extends Node {
    private final Collection<?> repr;

    public SeqNode(Graph owner, Collection<?> repr) {
        super(owner);
        this.repr = repr;
    }

    @Override
    public void resetEdges() {
        edges = new ArrayList<>();
        int i = 0;
        for (Object x : repr) {
            // detect attributes
            if (x != null && (x instanceof Number || x instanceof Boolean)) {
                edges.clear();
                return;
            }
            edges.add(new Edge(this, owner.getNodeFor(x), false, "" + i++));
        }
    }

    @Override
    public void resetAttributes() {
        attributes = new ArrayList<>();
        int i = 0;
        for (Object x : repr) {
            // detect edges
            if (!(x instanceof Number || x instanceof Boolean)) {
                attributes.clear();
                return;
            }
            attributes.add(new Attribute("" + i++, false, x));
        }
    }

    @Override
    public Object repr() {
        return repr;
    }

    @Override
    public String toString() {
        return (repr instanceof ArrayList<?> ? "Array#" : "List#") + System.identityHashCode(repr);
    }

}
