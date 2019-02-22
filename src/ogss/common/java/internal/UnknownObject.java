package ogss.common.java.internal;

public final class UnknownObject extends Obj {

    transient public final Pool<?> τPool;

    UnknownObject(Pool<?> τPool, int ID) {
        super(ID);
        this.τPool = τPool;
    }

    @Override
    public String typeName() {
        return τPool.name;
    }
}
