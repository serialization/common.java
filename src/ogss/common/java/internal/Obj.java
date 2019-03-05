package ogss.common.java.internal;

import ogss.common.java.api.FieldDeclaration;

/**
 * The root of the class instance hierarchy in OGSS.
 * 
 * @author Timm Felden
 * @note This type definition is in internal, because we have to protect the user from tampering with ID
 */
public abstract class Obj {

    /**
     * The constructor is protected to ensure that users do not break states accidentally
     * 
     * @note we have to pass ID to allow generated code to implement allocateInstances
     */
    protected Obj(int ID) {
        this.ID = ID;
    }

    /**
     * @return the type name of this object
     * @note this should be a class val
     */
    public abstract String typeName();

    /**
     * negative for new objects<br>
     * 0 for deleted objects<br>
     * everything else is the ID of an object inside a file
     * 
     * @note semantics of negative IDs may be subject to change without further notice
     */
    transient protected int ID;

    /**
     * Do not rely on ID if you do not know exactly what you are doing
     */
    public final int ID() {
        return ID;
    }

    /**
     * @return whether the object has been deleted
     */
    public boolean isDeleted() {
        return 0 == ID;
    }

    /**
     * potentially expensive but more pretty representation of this instance.
     */
    public final String prettyString(State sf) {
        StringBuilder sb = new StringBuilder("(this: ").append(this);
        Pool<?, ?> p = (Pool<?, ?>) sf.typeByName.get(typeName());
        FieldIterator fieldIterator = p.allFields();
        while (fieldIterator.hasNext()) {
            FieldDeclaration<?> f = fieldIterator.next();
            sb.append(", ").append(f.name()).append(": ").append(f.get(this));
        }
        return sb.append(")").toString();
    }
}
