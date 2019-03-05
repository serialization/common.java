package ogss.common.java.internal;

import java.util.Iterator;

/**
 * iterates efficiently over the type hierarchy
 * 
 * @author Timm Felden
 */
public class TypeHierarchyIterator<T extends Obj, B extends Builder<? extends T>>
        implements Iterator<Pool<? extends T, ? extends B>>, Iterable<Pool<? extends T, ? extends B>> {

    Pool<? extends T, ? extends B> p;
    final int end;

    public TypeHierarchyIterator(Pool<? extends T, ? extends B> pool) {
        p = pool;
        end = pool.THH;
    }

    @Override
    public boolean hasNext() {
        return null != p;
    }

    @Override
    public Pool<? extends T, ? extends B> next() {
        final Pool<? extends T, ? extends B> r = p;
        @SuppressWarnings("unchecked")
        final Pool<? extends T, ? extends B> n = (Pool<? extends T, ? extends B>) p.next;
        if (null != n && end < n.THH)
            p = n;
        else
            p = null;
        return r;
    }

    /**
     * @note valid, iff hasNext
     * @return the current element
     */
    public Pool<? extends T, ? extends B> get() {
        return p;
    }

    @Override
    public Iterator<Pool<? extends T, ? extends B>> iterator() {
        return this;
    }

}
