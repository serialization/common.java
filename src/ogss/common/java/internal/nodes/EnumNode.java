package ogss.common.java.internal.nodes;

import java.util.ArrayList;

import ogss.common.java.api.Graph;
import ogss.common.java.api.Node;
import ogss.common.java.internal.EnumProxy;

/**
 * A node wrapping an enum value.
 * 
 * @author Timm Felden
 */
public final class EnumNode extends Node {
    private final EnumProxy<?> repr;

    public EnumNode(Graph owner, EnumProxy<?> repr) {
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
        return repr.owner.toString() + "." + repr.name;
    }

}
