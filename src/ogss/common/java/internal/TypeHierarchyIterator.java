package ogss.common.java.internal;

import java.util.Iterator;

/**
 * iterates efficiently over the type hierarchy
 * 
 * @author Timm Felden
 */
public class TypeHierarchyIterator<T extends Obj>
        implements Iterator<Pool<? extends T>>, Iterable<Pool<? extends T>> {

    Pool<? extends T> p;
    final int end;

    public TypeHierarchyIterator(Pool<? extends T> pool) {
        p = pool;
        end = pool.THH;
    }

    @Override
    public boolean hasNext() {
        return null != p;
    }

    @Override
    public Pool<? extends T> next() {
        final Pool<? extends T> r = p;
        @SuppressWarnings("unchecked")
        final Pool<? extends T> n = (Pool<? extends T>) p.next;
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
    public Pool<? extends T> get() {
        return p;
    }

    @Override
    public Iterator<Pool<? extends T>> iterator() {
        return this;
    }

}
