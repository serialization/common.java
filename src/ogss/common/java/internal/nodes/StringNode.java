package ogss.common.java.internal.nodes;

import java.util.ArrayList;

import ogss.common.java.api.Graph;
import ogss.common.java.api.Node;

/**
 * A node wrapping a string.
 * 
 * @author Timm Felden
 */
public final class StringNode extends Node {
    private final String repr;

    public StringNode(Graph owner, String repr) {
        super(owner);
        this.repr = repr;
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
        return repr;
    }

    @Override
    public String toString() {
        return repr;
    }

}
