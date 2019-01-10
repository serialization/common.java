package ogss.common.java.internal;

import java.util.Iterator;

/**
 * iterates efficiently over the type hierarchy
 * 
 * @author Timm Felden
 */
public class TypeHierarchyIterator<T extends B, B extends Pointer>
        implements Iterator<Pool<? extends T, B>>, Iterable<Pool<? extends T, B>> {

    Pool<? extends T, B> p;
    final int end;

    public TypeHierarchyIterator(Pool<? extends T, B> pool) {
        p = pool;
        end = pool.typeHierarchyHeight;
    }

    @Override
    public boolean hasNext() {
        return null != p;
    }

    @Override
    public Pool<? extends T, B> next() {
        final Pool<? extends T, B> r = p;
        @SuppressWarnings("unchecked")
        final Pool<? extends T, B> n = (Pool<? extends T, B>) p.next;
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
    public Pool<? extends T, B> get() {
        return p;
    }

    @Override
    public Iterator<Pool<? extends T, B>> iterator() {
        return this;
    }

}
