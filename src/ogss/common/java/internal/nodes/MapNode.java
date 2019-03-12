package ogss.common.java.internal.nodes;

import java.util.ArrayList;
import java.util.Map;

import ogss.common.java.api.Attribute;
import ogss.common.java.api.Edge;
import ogss.common.java.api.Graph;
import ogss.common.java.api.Node;

/**
 * A node wrapping a Map. This is actually quite tricky in Java, because we cannot reconstruct our base types.
 * 
 * @author Timm Felden
 */
public final class MapNode extends Node {
    private final Map<?, ?> repr;

    public MapNode(Graph owner, Map<?, ?> repr) {
        super(owner);
        this.repr = repr;
    }

    @Override
    public void resetEdges() {
        edges = new ArrayList<>();
        for (Map.Entry<?, ?> x : repr.entrySet()) {
            Object k = x.getKey();
            Object v = x.getValue();
            // detect attributes
            if (v != null && (v instanceof Number || v instanceof Boolean)) {
                edges.clear();
                return;
            }
            edges.add(new Edge(this, owner.getNodeFor(v), false, k.toString()));
        }
    }

    @Override
    public void resetAttributes() {
        attributes = new ArrayList<>();
        for (Map.Entry<?, ?> x : repr.entrySet()) {
            Object k = x.getKey();
            Object v = x.getValue();
            // detect attributes
            if (!(v instanceof Number || v instanceof Boolean)) {
                attributes.clear();
                return;
            }
            attributes.add(new Attribute(k.toString(), false, v));
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
