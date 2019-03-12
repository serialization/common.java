package ogss.common.java.internal.nodes;

import java.util.ArrayList;

import ogss.common.java.api.Graph;
import ogss.common.java.api.Node;

/**
 * A node wrapping null.
 * 
 * @author Timm Felden
 */
public final class NullNode extends Node {
    public NullNode(Graph owner) {
        super(owner);
    }

    @Override
    public void resetEdges() {
        if (null == edges)
            edges = new ArrayList<>();
    }

    @Override
    public void resetAttributes() {
        if (null == attributes)
            attributes = new ArrayList<>();
    }

    @Override
    public Object repr() {
        return null;
    }

    @Override
    public String toString() {
        return "null";
    }

}
