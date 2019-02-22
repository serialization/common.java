package ogss.common.java.internal;

import java.lang.reflect.Constructor;

/**
 * A generic sub pool class that creates new objects via reflection to reduce the amount of generated code.
 * 
 * @author Timm Felden
 */
public class SubPool<T extends Obj> extends Pool<T> {
    /**
     * The class of instances of this pool
     */
    private final Class<T> cls;

    public SubPool(int poolIndex, String name, Pool<? super T> superPool, Class<T> cls) {
        super(poolIndex, name, superPool, myKFN, (Class[]) myKFC, 0);
        this.cls = cls;
    }

    @Override
    protected SubPool<T> makeSubPool(int index, String name) {
        return new SubPool<>(index, name, this, cls);
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
}
