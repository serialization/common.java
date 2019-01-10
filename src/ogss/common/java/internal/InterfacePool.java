package ogss.common.java.internal;

import java.io.IOException;

import ogss.common.java.api.GeneralAccess;
import ogss.common.streams.InStream;
import ogss.common.streams.OutStream;

/**
 * Holds interface instances. Serves as an API realization. Ensures correctness
 * of reflective type system.
 * 
 * @note unfortunately, one cannot prove that T extends SkillObject. Hence, we
 *       cannot inherit from Access<T>
 *
 * @note typing in this implementation is intentionally incorrect, because java
 *       does not permit interfaces to inherit from classes
 * 
 * @author Timm Felden
 */
final public class InterfacePool<T, B extends Pointer> extends FieldType<T> implements GeneralAccess<T> {

    final private String name;
    final public Pool<? extends Pointer, B> superPool;
    final private Pool<? extends B, B>[] realizations;

    /**
     * Construct an interface pool.
     * 
     * @note realizations must be in type order
     */
    @SafeVarargs
    public InterfacePool(String name, Pool<?, B> superPool, Pool<? extends B, B>... realizations) {
        super(superPool.typeID);
        this.name = name;
        this.superPool = superPool;
        this.realizations = realizations;
    }

    @Override
    public int size() {
        int rval = 0;
        for (Pool<? extends B, B> p : realizations) {
            rval += p.size();
        }
        return rval;
    }

    /***
     * @note cast required to work around weakened type system by javac 1.8.131
     */
    @SuppressWarnings("unchecked")
    @Override
    final public InterfaceIterator<T> iterator() {
        return new InterfaceIterator<T>((Pool<Pointer, Pointer>[]) realizations);
    }

    @Override
    final public State owner() {
        return superPool.owner();
    }

    @Override
    final public String name() {
        return name;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T r(InStream in) {
        int index = in.v32() - 1;
        final Pointer[] data = superPool.data;
        if (index < 0 | data.length <= index)
            return null;
        return (T) data[index];
    }

    @Override
    public void w(T data, OutStream out) throws IOException {
        if (null == data)
            out.i8((byte) 0);
        else
            out.v64(((Pointer) data).ID);
    }

    @Override
    public String toString() {
        return name;
    }
}
