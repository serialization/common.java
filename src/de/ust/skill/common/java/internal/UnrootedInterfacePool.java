package de.ust.skill.common.java.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import de.ust.skill.common.java.api.GeneralAccess;
import de.ust.skill.common.java.internal.fieldTypes.Annotation;
import de.ust.skill.common.java.iterators.Iterators;
import de.ust.skill.common.jvm.streams.InStream;
import de.ust.skill.common.jvm.streams.OutStream;

/**
 * Holds interface instances. Serves as an API realization. Ensures correctness
 * of reflective type system.
 * 
 * @note in this case, the super type is annotation
 * @note unfortunately, one cannot prove that T extends SkillObject. Hence, we
 *       cannot inherit from Access<T>
 *
 * @author Timm Felden
 */
final public class UnrootedInterfacePool<T> extends FieldType<T> implements GeneralAccess<T> {

    final private String name;
    final private Annotation superType;
    /**
     * @note the Java type system seems to be to weak to prove the correct type
     *       [? extends T, ?]
     */
    final private StoragePool<T, ?>[] realizations;

    /**
     * Construct an interface pool.
     * 
     * @note realizations must be in type order
     * @note realizations must be of type StoragePool<? extends T, ?>
     */
    public UnrootedInterfacePool(String name, Annotation superPool, StoragePool<?, ?>... realizations) {
        super(superPool.typeID());
        this.name = name;
        this.superType = superPool;
        this.realizations = (StoragePool<T, ?>[]) realizations;
    }

    @Override
    public int size() {
        int rval = 0;
        for (StoragePool<T, ?> p : realizations) {
            rval += p.size();
        }
        return rval;
    }

    @Override
    public Iterator<T> iterator() {
        ArrayList<Iterator<? extends T>> iters = new ArrayList<>(realizations.length);
        for (StoragePool<T, ?> p : realizations) {
            iters.add(p.iterator());
        }
        return Iterators.concatenate(iters);
    }

    public String name() {
        return name;
    }

    public Iterator<T> typeOrderIterator() {
        ArrayList<Iterator<? extends T>> iters = new ArrayList<>(realizations.length);
        for (StoragePool<T, ?> p : realizations) {
            iters.add(p.typeOrderIterator());
        }
        return Iterators.concatenate(iters);
    }

    @Override
    public T readSingleField(InStream in) {
        return (T) superType.readSingleField(in);
    }

    @Override
    public long calculateOffset(Collection<T> xs) {
        return superType.calculateOffset(cast(xs));
    }

    @Override
    public final long singleOffset(T x) {
        return superType.singleOffset((SkillObject) x);
    }

    @Override
    public void writeSingleField(T data, OutStream out) throws IOException {
        superType.writeSingleField((SkillObject) data, out);
    }

    /**
     * hide cast that is required because interfaces do not inherit from classes
     */
    private final <V, U> Collection<U> cast(Collection<V> xs) {
        return (Collection<U>) xs;
    }

    /**
     * hide cast that is required because interfaces do not inherit from classes
     */
    private final <V, U> U cast(V x) {
        return (U) x;
    }

    public Annotation getType() {
        return superType;
    }

    @Override
    public String toString() {
        return name;
    }
}
