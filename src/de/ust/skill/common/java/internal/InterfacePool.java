package de.ust.skill.common.java.internal;

import java.io.IOException;
import java.util.Collection;

import de.ust.skill.common.java.api.GeneralAccess;
import de.ust.skill.common.java.api.SkillFile;
import de.ust.skill.common.java.internal.fieldTypes.V64;
import de.ust.skill.common.jvm.streams.InStream;
import de.ust.skill.common.jvm.streams.OutStream;

/**
 * Holds interface instances. Serves as an API realization. Ensures correctness
 * of reflective type system.
 * 
 * @note unfortunately, one cannot prove that T extends SkillObject. Hence, we
 *       cannot inherit from Access<T>
 *
 * @author Timm Felden
 */
final public class InterfacePool<T, B extends SkillObject> extends FieldType<T> implements GeneralAccess<T> {

    final private String name;
    final public StoragePool<? extends SkillObject, B> superPool;
    final private StoragePool<? extends T, B>[] realizations;

    /**
     * Construct an interface pool.
     * 
     * @note realizations must be in type order
     */
    @SafeVarargs
    public InterfacePool(String name, StoragePool<?, B> superPool, StoragePool<? extends T, B>... realizations) {
        super(superPool.typeID);
        this.name = name;
        this.superPool = superPool;
        this.realizations = realizations;
    }

    @Override
    public int size() {
        int rval = 0;
        for (StoragePool<? extends T, B> p : realizations) {
            rval += p.size();
        }
        return rval;
    }

    @Override
    final public InterfaceIterator<T> iterator() {
        return new InterfaceIterator<>(realizations);
    }

    final public SkillFile owner() {
        return superPool.owner();
    }

    final public String name() {
        return name;
    }

    final public String superName() {
        return superPool.name;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T readSingleField(InStream in) {
        int index = in.v32() - 1;
        B[] data = superPool.data;
        if (index < 0 | data.length <= index)
            return null;
        return (T) data[index];
    }

    @Override
    public long calculateOffset(Collection<T> xs) {
        // shortcut small compressed types
        if (superPool.data.length < 128)
            return xs.size();

        long result = 0L;
        for (T x : xs) {
            result += null == x ? 1 : V64.singleV64Offset(((SkillObject) x).skillID);
        }
        return result;
    }

    @Override
    public final long singleOffset(T x) {
        return null == x ? 1 : V64.singleV64Offset(((SkillObject) x).skillID);
    }

    @Override
    public void writeSingleField(T data, OutStream out) throws IOException {
        if (null == data)
            out.i8((byte) 0);
        else
            out.v64(((SkillObject) data).skillID);
    }

    @Override
    public String toString() {
        return name;
    }
}
