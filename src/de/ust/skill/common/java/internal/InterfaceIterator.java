package de.ust.skill.common.java.internal;

import java.util.Iterator;

public final class InterfaceIterator<T> implements Iterator<T> {

    private final StoragePool<? extends T, ?>[] ps;
    private int i;
    private DynamicDataIterator<? extends T, ?> xs;

    public <B extends SkillObject> InterfaceIterator(StoragePool<? extends T, ?>[] realizations) {
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
    public T next() {
        T r = xs.next();
        if (!xs.hasNext())
            while (i < ps.length) {
                xs = ps[i++].iterator();
            }

        return r;
    }

}
