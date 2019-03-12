package ogss.common.java.api;

import java.util.ArrayList;

/**
 * Wraps an object in the underlying graph to provide a node-edge based view onto the graph.
 * 
 * @author Timm Felden
 */
public abstract class Node {

    protected Node(Graph owner) {
        this.owner = owner;
    }

    protected ArrayList<Edge> edges;

    /**
     * Get edges where this is Edge.from.
     * 
     * @note edges and target nodes are created on first call of this function; subsequent calls will always return the
     *       same result;
     */
    public final Iterable<Edge> edges() {
        if (null == edges) {
            resetEdges();
        }
        return edges;
    }

    /**
     * Force recalculation of edges.
     */
    public abstract void resetEdges();

    protected ArrayList<Attribute> attributes;

    /**
     * Get attributes of this node.
     */
    public final Iterable<Attribute> attributes() {
        if (null == attributes) {
            resetAttributes();
        }
        return attributes;
    }

    /**
     * Force recalculation of attributes.
     */
    public abstract void resetAttributes();

    /**
     * Reset edges and attributes.
     */
    public final void reset() {
        resetAttributes();
        resetEdges();
    }

    /**
     * @return the object represented by this node
     */
    public abstract Object repr();

    /**
     * @return the owner of this node
     */
    public final Graph owner;

    @Override
    public abstract String toString();
}
