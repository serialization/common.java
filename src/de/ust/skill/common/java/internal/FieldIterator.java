package de.ust.skill.common.java.internal;

import java.util.Iterator;

public final class FieldIterator implements Iterator<FieldDeclaration<?, ?>> {

    private StoragePool<?, ?> p;
    private int i;

    FieldIterator(StoragePool<?, ?> p) {
        this.p = p;
        this.i = -p.autoFields.length;
        while (this.p != null && i == 0 && 0 == this.p.dataFields.size()) {
            this.p = this.p.superPool;
            if (this.p != null)
                this.i = -this.p.autoFields.length;
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
            do {
                p = p.superPool;
                if (p != null)
                    i = -p.autoFields.length;
            } while (p != null && i == 0 && 0 == p.dataFields.size());
        }
        return f;
    }

}
