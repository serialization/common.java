package ogss.common.java.internal;

import java.util.Iterator;

/**
 * Iterates efficiently over static instances of a pool.
 *
 * @author Timm Felden
 */
public class StaticDataIterator<T extends Obj> implements Iterator<T> {

    // ! target pool
    final Pool<T> p;

    int index;
    int last;

    boolean second;

    public StaticDataIterator(Pool<T> storagePool) {
        p = storagePool;
        // @note other members are zero-allocated

        // find first valid position
        index = p.bpo;
        last = index + p.staticDataInstances;

        // mode switch, if there is no other block
        if (index == last) {
            second = true;
            index = 0;
            last = p.newObjects.size();
        }
    }

    @Override
    public boolean hasNext() {
        return index != last;
    }

    @Override
    public T next() {
        if (!second) {
            @SuppressWarnings("unchecked")
            T r = (T) p.data[index];
            index++;
            if (index == last) {
                second = true;
                index = 0;
                last = p.newObjects.size();
            }
            return r;
        }

        T r = p.newObject(index);
        index++;
        return r;
    }

}
