package de.ust.skill.common.java.internal;

import java.util.Iterator;

/**
 * Iterates efficiently over dynamic instances of a pool in type order.
 *
 * @author Timm Felden
 * @note cast required to work around weakened type system by javac 1.8.131
 */
@SuppressWarnings("unchecked")
public class TypeOrderIterator<T extends B, B extends SkillObject> implements Iterator<T> {

    final TypeHierarchyIterator<T, B> ts;
    StaticDataIterator<T> is;

    public TypeOrderIterator(StoragePool<T, B> storagePool) {
        ts = new TypeHierarchyIterator<T, B>(storagePool);
        while (ts.hasNext()) {
            StoragePool<T, B> t = (StoragePool<T, B>) ts.next();
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
                StoragePool<T, B> t = (StoragePool<T, B>) ts.next();
                if (0 != t.staticSize()) {
                    is = new StaticDataIterator<T>(t);
                    break;
                }
            }
        }
        return result;
    }

}
