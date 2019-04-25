package ogss.common.java.internal;

import java.io.IOException;

import ogss.common.java.api.GeneralAccess;
import ogss.common.streams.InStream;
import ogss.common.streams.OutStream;

/**
 * Holds interface instances. Serves as an API realization. Ensures correctness of reflective type system.
 * 
 * @note in this case, the super type is annotation
 * @note unfortunately, one cannot prove that T extends Obj. Hence, we cannot inherit from Access<T>
 * @note typing in this implementation is intentionally incorrect, because java does not permit interfaces to inherit
 *       from classes
 * @author Timm Felden
 */
final public class UnrootedInterfacePool<T> extends FieldType<T> implements GeneralAccess<T> {

    final private String name;
    final private AnyRefType superType;
    /**
     * @note the Java type system seems to be to weak to prove the correct type [? extends T]
     */
    final private Pool<Obj>[] realizations;

    /**
     * Construct an interface pool.
     * 
     * @note realizations must be in type order
     * @note realizations must be of type StoragePool<? extends T>
     */
    @SuppressWarnings("unchecked")
    public UnrootedInterfacePool(String name, AnyRefType superPool, Pool<?>... realizations) {
        super(AnyRefType.typeID);
        this.name = name;
        this.superType = superPool;
        this.realizations = (Pool<Obj>[]) realizations;
    }

    @Override
    public int size() {
        int rval = 0;
        for (Pool<?> p : realizations) {
            rval += p.size();
        }
        return rval;
    }

    @Override
    final public InterfaceIterator<T> iterator() {
        return new InterfaceIterator<T>(realizations);
    }

    @Override
    final public String name() {
        return name;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T r(InStream in) {
        return (T) superType.r(in);
    }

    @Override
    public boolean w(T data, OutStream out) throws IOException {
        return superType.w(data, out);
    }

    public AnyRefType getType() {
        return superType;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public State owner() {
        return realizations[0].owner;
    }

    @Override
    public T get(int ID) {
        throw new NoSuchMethodError();
    }
}
