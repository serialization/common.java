package ogss.common.java.internal;

import java.util.Iterator;

/**
 * Returns all instances for an interface pool.
 * 
 * @author Timm Felden
 * @note typing in this implementation is intentionally incorrect, because java does not permit interfaces to inherit
 *       from classes
 */
public final class InterfaceIterator<T> implements Iterator<T> {

    private final Pool<Obj>[] ps;
    private int i;
    private DynamicDataIterator<Obj> xs;

    public InterfaceIterator(Pool<Obj>[] realizations) {
        ps = realizations;
        while (i < ps.length) {
            xs = ps[i++].iterator();
        }
    }

    @Override
    public boolean hasNext() {
        return xs.hasNext();
    }

    @Override
    @SuppressWarnings("unchecked")
    public T next() {
        T r = (T) xs.next();
        if (!xs.hasNext())
            while (i < ps.length) {
                xs = ps[i++].iterator();
            }

        return r;
    }

}
