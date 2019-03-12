package ogss.common.java.internal;

/**
 * Represents an instance of an unknown type hierarchy.
 * 
 * @author Timm Felden
 */
public final class UnknownObject extends Obj implements NamedObj {

    transient public final Pool<?> τp;

    public UnknownObject(Pool<?> τp, int ID) {
        super(ID);
        this.τp = τp;
    }

    @Override
    public int stid() {
        return -1;
    }

    @Override
    public Pool<?> τp() {
        return τp;
    }
}
