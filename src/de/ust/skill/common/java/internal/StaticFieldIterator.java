package de.ust.skill.common.java.internal;

import java.util.Iterator;

/**
 * Iterate over fields of a single pool ignoring super pools.
 * 
 * @author Timm Felden
 *
 */
public final class StaticFieldIterator implements Iterator<FieldDeclaration<?, ?>> {

    private StoragePool<?, ?> p;
    private int i;

    StaticFieldIterator(StoragePool<?, ?> p) {
        if (p.autoFields.length == 0 && 0 == p.dataFields.size()) {
            this.p = null;
            this.i = 0;
        } else {
            this.p = p;
            this.i = -p.autoFields.length;
        }
    }

    @Override
    public boolean hasNext() {
        return p != null;
    }

    @Override
    public FieldDeclaration<?, ?> next() {
        FieldDeclaration<?, ?> f = i < 0 ? p.autoFields[-1 - i] : p.dataFields.get(i);
        i++;
        if (i == p.dataFields.size()) {
            p = null;
        }
        return f;
    }

}
