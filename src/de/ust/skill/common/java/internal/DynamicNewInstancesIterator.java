package de.ust.skill.common.java.internal;

import java.util.Iterator;

/**
 * Iterates efficiently over dynamic new instances of a pool.
 *
 * Like second phase of dynamic data iterator.
 *
 * @author Timm Felden
 */
public final class DynamicNewInstancesIterator<T extends B, B extends SkillObject> implements Iterator<T> {

    final TypeHierarchyIterator<T, B> ts;

    int index;
    int last;

    public DynamicNewInstancesIterator(StoragePool<T, B> storagePool) {
        ts = new TypeHierarchyIterator<>(storagePool);
        last = storagePool.newObjects.size();

        while (0 == last && ts.hasNext()) {
            ts.next();
            if (ts.hasNext())
                last = ts.get().newObjects.size();
            else
                return;
        }
    }

    @Override
    public boolean hasNext() {
        return index != last;
    }

    @Override
    public T next() {
        T rval = ts.get().newObject(index);
        index++;
        if (index == last && ts.hasNext()) {
            index = last = 0;
            do {
                ts.next();
                if (ts.hasNext())
                    last = ts.get().newObjects.size();
                else
                    return rval;
            } while (0 == last && ts.hasNext());

        }
        return rval;
    }

}
