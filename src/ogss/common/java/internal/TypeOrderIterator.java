package ogss.common.java.internal;

import java.util.Iterator;

/**
 * Iterates efficiently over dynamic instances of a pool in type order.
 *
 * @author Timm Felden
 * @note cast required to work around weakened type system by javac 1.8.131
 */
@SuppressWarnings("unchecked")
public class TypeOrderIterator<T extends Obj> implements Iterator<T> {

    final TypeHierarchyIterator<T, ?> ts;
    StaticDataIterator<T> is;

    public TypeOrderIterator(Pool<T, ?> pool) {
        ts = new TypeHierarchyIterator<>(pool);
        while (ts.hasNext()) {
            Pool<T, ?> t = (Pool<T, ?>) ts.next();
            if (0 != t.staticSize()) {
                is = new StaticDataIterator<T>(t);
                break;
            }
        }
    }

    @Override
    public boolean hasNext() {
        return is != null && is.hasNext();
    }

    @Override
    public T next() {
        T result = is.next();
        if (!is.hasNext()) {
            while (ts.hasNext()) {
                Pool<T, ?> t = (Pool<T, ?>) ts.next();
                if (0 != t.staticSize()) {
                    is = new StaticDataIterator<T>(t);
                    break;
                }
            }
        }
        return result;
    }

}
