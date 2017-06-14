package de.ust.skill.common.java.internal;

import java.util.Iterator;

/**
 * iterates efficiently over the type hierarchy
 * 
 * @author Timm Felden
 */
public class TypeHierarchyIterator<T extends B, B extends SkillObject>
        implements Iterator<StoragePool<? extends T, B>>, Iterable<StoragePool<? extends T, B>> {

    StoragePool<? extends T, B> p;
    final int end;

    public TypeHierarchyIterator(StoragePool<? extends T, B> pool) {
        p = pool;
        end = pool.typeHierarchyHeight;
    }

    @Override
    public boolean hasNext() {
        return null != p;
    }

    @Override
    public StoragePool<? extends T, B> next() {
        final StoragePool<? extends T, B> r = p;
        @SuppressWarnings("unchecked")
        final StoragePool<? extends T, B> n = (StoragePool<? extends T, B>) p.nextPool;
        if (null != n && end < n.typeHierarchyHeight)
            p = n;
        else
            p = null;
        return r;
    }

    /**
     * @note valid, iff hasNext
     * @return the current element
     */
    public StoragePool<? extends T, B> get() {
        return p;
    }

    @Override
    public Iterator<StoragePool<? extends T, B>> iterator() {
        return this;
    }

}
