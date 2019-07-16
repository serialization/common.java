package ogss.common.java.internal;

import java.lang.reflect.Constructor;

import ogss.common.java.api.OGSSException;

/**
 * A generic sub pool class that creates new objects via reflection to reduce the amount of generated code.
 * 
 * @author Timm Felden
 */
public final class SubPool<T extends Obj> extends Pool<T> {
    /**
     * The class of instances of this pool
     */
    private final Class<T> cls;

    public SubPool(int poolIndex, String name, Class<T> cls, Pool<? super T> superPool) {
        super(poolIndex, name, superPool, 0);
        this.cls = cls;
    }

    @Override
    protected SubPool<T> makeSub(int index, String name) {
        return new SubPool<>(index, name, cls, this);
    }

    @Override
    protected void allocateInstances() {
        int i = bpo, j;
        final int high = i + staticDataInstances;
        try {
            Constructor<T> make = cls.getConstructor(Pool.class, int.class);
            while (i < high) {
                data[i] = make.newInstance(this, j = (i + 1));
                i = j;
            }
        } catch (Exception e) {
            throw new RuntimeException("internal error", e);
        }
    }

    @Override
    public T make() throws OGSSException {
        final T rval;
        try {
            rval = cls.getConstructor(Pool.class, int.class).newInstance(this, 0);
        } catch (Exception e) {
            throw new OGSSException("reflective make failed", e);
        }
        add(rval);
        return rval; 
    }
}
