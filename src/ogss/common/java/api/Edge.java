package ogss.common.java.api;

/**
 * Wraps a pointer from one node to another.
 * 
 * @author Timm Felden
 */
public final class Edge {

    public Edge(Node from, Node to, boolean isTransient, String name) {
        this.from = from;
        this.to = to;
        this.isTransient = isTransient;
        this.name = name;
    }

    public final boolean isTransient;

    public final Node from;
    public final Node to;

    public final String name;

    @Override
    public String toString() {
        return name;
    }
}
