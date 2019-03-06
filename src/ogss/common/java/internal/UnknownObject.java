package ogss.common.java.internal;

/**
 * Represents an instance of an unknown type hierarchy.
 * 
 * @author Timm Felden
 */
public final class UnknownObject extends Obj {

    transient public final Pool<?> τPool;

    public UnknownObject(Pool<?> τPool, int ID) {
        super(ID);
        this.τPool = τPool;
    }

    @Override
    public String typeName() {
        return τPool.name;
    }
}
