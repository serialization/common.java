package de.ust.skill.common.java.internal;

import java.io.IOException;
import java.util.Collection;

import de.ust.skill.common.java.api.GeneralAccess;
import de.ust.skill.common.java.api.SkillFile;
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
    final private StoragePool<? extends SkillObject, B> superPool;
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
        return (T) superPool.readSingleField(in);
    }

    @Override
    public long calculateOffset(Collection<T> xs) {
        return superPool.calculateOffset(cast(xs));
    }

    @Override
    public final long singleOffset(T x) {
        if (null == x)
            return 1L;

        long v = ((SkillObject) x).skillID;
        if (0L == (v & 0xFFFFFFFFFFFFFF80L)) {
            return 1;
        } else if (0L == (v & 0xFFFFFFFFFFFFC000L)) {
            return 2;
        } else if (0L == (v & 0xFFFFFFFFFFE00000L)) {
            return 3;
        } else if (0L == (v & 0xFFFFFFFFF0000000L)) {
            return 4;
        } else if (0L == (v & 0xFFFFFFF800000000L)) {
            return 5;
        } else if (0L == (v & 0xFFFFFC0000000000L)) {
            return 6;
        } else if (0L == (v & 0xFFFE000000000000L)) {
            return 7;
        } else if (0L == (v & 0xFF00000000000000L)) {
            return 8;
        } else {
            return 9;
        }
    }

    @Override
    public void writeSingleField(T data, OutStream out) throws IOException {
        superPool.writeSingleField(cast(data), out);
    }

    /**
     * hide cast that is required because interfaces do not inherit from classes
     */
    @SuppressWarnings("unchecked")
    private static final <V, U> U cast(V x) {
        return (U) x;
    }

    @Override
    public String toString() {
        return name;
    }
}
