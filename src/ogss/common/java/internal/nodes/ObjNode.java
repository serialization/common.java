package ogss.common.java.internal.nodes;

import java.util.ArrayList;

import ogss.common.java.api.Attribute;
import ogss.common.java.api.Edge;
import ogss.common.java.api.Graph;
import ogss.common.java.api.Node;
import ogss.common.java.internal.FieldDeclaration;
import ogss.common.java.internal.Obj;
import ogss.common.java.internal.Pool;
import ogss.common.java.internal.fieldDeclarations.AutoField;

/**
 * A node wrapping an Obj.
 * 
 * @author Timm Felden
 */
public final class ObjNode extends Node {
    private final Obj repr;

    public ObjNode(Graph owner, Obj repr) {
        super(owner);
        this.repr = repr;
    }

    @Override
    public void resetEdges() {
        edges = new ArrayList<>();
        Pool<?> p = owner.owner.pool(repr);
        for (FieldDeclaration<?, ?> f : p.allFields()) {
            // skip attributes
            if (f.type.typeID < 8)
                continue;

            edges.add(new Edge(this, owner.getNodeFor(f.get(repr)), f instanceof AutoField<?, ?>, f.name()));
        }
    }

    @Override
    public void resetAttributes() {
        attributes = new ArrayList<>();
        Pool<?> p = owner.owner.pool(repr);
        for (FieldDeclaration<?, ?> f : p.allFields()) {
            // skip edges
            if (f.type.typeID >= 8)
                continue;

            attributes.add(new Attribute(f.name(), f instanceof AutoField<?, ?>, f.get(repr)));
        }
    }

    @Override
    public Object repr() {
        return repr;
    }

    @Override
    public String toString() {
        return owner.owner.typeName(repr) + "#" + repr.ID();
    }

}
