package ogss.common.java.internal;

import java.util.Iterator;

/**
 * Iterates efficiently over dynamic instances of a pool. First phase will iterate over all blocks of the pool. The
 * second phase will iterate over all dynamic instances of the pool.
 *
 * @author Timm Felden
 */
public final class DynamicDataIterator<T extends Obj> implements Iterator<T> {

    Pool<? extends T> p;

    final int endHeight;
    int index;
    int last;
    // true if in second phase
    boolean second;

    public DynamicDataIterator(Pool<T> storagePool) {
        p = storagePool;
        endHeight = p.THH;
        // other fields are zero-allocated

        // find an instance in first phase
        index = p.bpo;
        last = index + p.cachedSize;

        // mode switch, if no values obtained from data
        if (index == last) {
            second = true;
            while (null != p) {
                if (p.newObjects.size() != 0) {
                    index = 0;
                    last = p.newObjects.size();
                    break;
                }
                nextP();
            }
        }
    }

    @Override
    public boolean hasNext() {
        return null != p;
    }

    @Override
    public T next() {
        if (!second) {
            @SuppressWarnings("unchecked")
            T r = (T) p.data[index];
            index++;
            // mode switch, as we reached the end of data
            if (index == last) {
                second = true;
                while (null != p) {
                    if (p.newObjects.size() != 0) {
                        index = 0;
                        last = p.newObjects.size();
                        break;
                    }
                    nextP();
                }
            }
            return r;
        }

        T r = p.newObjects.get(index);
        index++;
        if (index == last) {
            do {
                nextP();
                if (null == p)
                    break;

                if (p.newObjects.size() != 0) {
                    index = 0;
                    last = p.newObjects.size();
                    break;
                }
            } while (true);
        }
        return r;
    }

    private void nextP() {
        @SuppressWarnings("unchecked")
        final Pool<? extends T> n = (Pool<? extends T>) p.next;
        if (null != n && endHeight < n.THH)
            p = n;
        else
            p = null;
    }
}
