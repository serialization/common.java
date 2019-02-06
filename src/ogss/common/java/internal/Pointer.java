package ogss.common.java.internal;

import ogss.common.java.api.FieldDeclaration;

/**
 * The root of the hierarchy of pointer types in OGSS. It is called anyRef in the specification language, but that would
 * conflict with AnyRef in Scala.
 * 
 * @author Timm Felden
 * @note This type definition is in internal, because we have to protect setSkillID from the user
 */
public abstract class Pointer {

    /**
     * The constructor is protected to ensure that users do not break states accidentally
     */
    protected Pointer(int ID) {
        this.ID = ID;
    }

    /**
     * @return the type name of this object
     */
    public abstract String typeName();

    /**
     * -1 for new objects<br>
     * 0 for deleted objects<br>
     * everything else is the ID of an object inside of a file
     * 
     * @note semantics of negative IDs may be subject to change without further notice
     */
    transient protected int ID;

    /**
     * Do not rely on ogss ID if you do not know exactly what you are doing.
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

    public static final class SubType extends Pointer {

        transient public final Pool<?, ?> τPool;

        SubType(Pool<?, ?> τPool, int ID) {
            super(ID);
            this.τPool = τPool;
        }

        @Override
        public String typeName() {
            return τPool.name;
        }
    }
}
