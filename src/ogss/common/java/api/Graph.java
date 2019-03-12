package ogss.common.java.api;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import ogss.common.java.internal.EnumProxy;
import ogss.common.java.internal.Obj;
import ogss.common.java.internal.State;
import ogss.common.java.internal.nodes.EnumNode;
import ogss.common.java.internal.nodes.MapNode;
import ogss.common.java.internal.nodes.NullNode;
import ogss.common.java.internal.nodes.ObjNode;
import ogss.common.java.internal.nodes.SeqNode;
import ogss.common.java.internal.nodes.SetNode;
import ogss.common.java.internal.nodes.StringNode;

/**
 * A Graph view onto an object graph. Nodes and edges created during Graph traversal are cached by this object and
 * specific for it, i.e. if you create two graph views for the same file, the resulting nodes will be distinct.
 * 
 * @note the current implementation of the Graph-API assumes that the graph is unchanged.
 * @author Timm Felden
 */
public final class Graph {

    private final IdentityHashMap<Object, Node> knownNodes = new IdentityHashMap<>();

    private final NullNode nullptr = new NullNode(this);

    public final State owner;

    public Graph(State owner) {
        this.owner = owner;
    }

    /**
     * @return a node for an argument object
     * @note the argument must be owned by the owner of this graph
     */
    public Node getNodeFor(Object n) {
        if (null == n)
            return nullptr;

        Node r = knownNodes.get(n);
        if (null == r) {
            if (n instanceof Obj) {
                r = new ObjNode(this, (Obj) n);
            } else if (n instanceof EnumProxy<?>) {
                r = new EnumNode(this, (EnumProxy<?>) n);
            } else if (n instanceof String) {
                r = new StringNode(this, (String) n);
            } else if (n instanceof Set<?>) {
                r = new SetNode(this, (Set<?>) n);
            } else if (n instanceof Collection<?>) {
                r = new SeqNode(this, (Collection<?>) n);
            } else if (n instanceof Map<?, ?>) {
                r = new MapNode(this, (Map<?, ?>) n);
            } else {
                throw new Error("Missed a case: " + n);
            }
            knownNodes.put(n, r);
        }
        return r;
    }
}
