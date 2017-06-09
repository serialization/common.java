package de.ust.skill.common.java.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import de.ust.skill.common.java.api.GeneralAccess;
import de.ust.skill.common.java.api.SkillFile;
import de.ust.skill.common.java.iterators.Iterators;
import de.ust.skill.common.jvm.streams.InStream;
import de.ust.skill.common.jvm.streams.OutStream;

/**
 * Holds interface instances. Serves as an API realization. Ensures correctness
 * of reflective type system.
 * 
 * @note unfortunately, one cannot prove that T extends SkillObject. Hence, we cannot inherit from Access<T>
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
    public boolean isEmpty() {
        return 0 == size();
    }

    @Override
    public boolean contains(Object o) {
        for (StoragePool<? extends T, B> p : realizations) {
            if (p.contains(o))
                return true;
        }
        return false;
    }

    @Override
    public Iterator<T> iterator() {
        ArrayList<Iterator<? extends T>> iters = new ArrayList<>(realizations.length);
        for (StoragePool<? extends T, B> p : realizations) {
            iters.add(p.iterator());
        }
        return Iterators.concatenate(iters);
    }

    @Override
    public Object[] toArray() {
        throw new NoSuchMethodError("TODO");
    }

    @Override
    public <T> T[] toArray(T[] a) {
        throw new NoSuchMethodError("TODO");
    }

    @Override
    public boolean add(T e) {
        throw new NoSuchMethodError("TODO");
    }

    @Override
    public boolean remove(Object o) {
        throw new NoSuchMethodError("TODO");
    }

    @Override
    public boolean containsAll(Collection<?> xs) {
        for (Object o : xs) {
            if (!contains(o))
                return false;
        }
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        throw new NoSuchMethodError("TODO");
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new NoSuchMethodError("TODO");
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new NoSuchMethodError("TODO");
    }

    @Override
    public void clear() {
        throw new NoSuchMethodError("TODO");
    }

    public SkillFile owner() {
        return superPool.owner();
    }

    public String name() {
        return name;
    }

    public String superName() {
        return superPool.name;
    }

    public Iterator<T> typeOrderIterator() {
        ArrayList<Iterator<? extends T>> iters = new ArrayList<>(realizations.length);
        for (StoragePool<? extends T, B> p : realizations) {
            iters.add(p.typeOrderIterator());
        }
        return Iterators.concatenate(iters);
    }

    @Override
    public T readSingleField(InStream in) {
        return (T) superPool.readSingleField(in);
    }

    @Override
    public long calculateOffset(Collection<T> xs) {
        return superPool.calculateOffset(cast(xs));
    }

    @Override
    public void writeSingleField(T data, OutStream out) throws IOException {
        superPool.writeSingleField(cast(data), out);
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

    @Override
    public String toString() {
        return name;
    }
}
