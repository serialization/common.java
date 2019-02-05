package ogss.common.java.internal;

import java.util.ArrayList;

import ogss.common.java.internal.fieldDeclarations.AutoField;

/**
 * The base of a type hierarchy. Contains optimized representations of data compared to sub pools.
 * 
 * @author Timm Felden
 * @param <T>
 */
public class BasePool<T extends Pointer> extends Pool<T, T> {

    /**
     * the owner is set once by the SkillState.finish method!
     */
    protected State owner = null;

    protected BasePool(int poolIndex, String name, String[] knownFields, Class<KnownDataField<?, T>>[] KFC,
            AutoField<?, T>[] autoFields) {
        super(poolIndex, name, null, knownFields, KFC, autoFields);
    }

    @Override
    public final State owner() {
        return owner;
    }

    /**
     * compress new instances into the data array and update object IDs
     */
    final void compress(int[] bpoMap) {

        // create our part of the bpo map
        {
            int next = 0;
            Pool<? extends T, T> p = this;

            do {
                bpoMap[p.typeID - 10] = next;
                final int s = p.staticSize() - p.deletedCount;
                p.cachedSize = s;
                next += s;
                p = p.next;
            } while (null != p);
        }

        // calculate correct dynamic size for all sub pools
        {
            ArrayList<Pool<?, ?>> cs = owner.classes;
            for (int i = cs.size();;) {
                --i;
                Pool<?, ?> p = cs.get(i);
                if (this == p)
                    break;
                if (this == p.basePool) {
                    p.superPool.cachedSize += p.cachedSize;
                }
            }
        }

        // note: we could move the object update to updateAfterCompress and
        // perform that in parallel (because it is much easier without append)

        // from now on, size will take deleted objects into account, thus d may
        // in fact be smaller then data!
        Pointer[] d = new Pointer[cachedSize];
        int pos = 0;
        TypeOrderIterator<T, T> is = typeOrderIterator();
        while (is.hasNext()) {
            final T i = is.next();
            if (i.ID != 0) {
                d[pos++] = i;
                i.ID = pos;
            }
        }

        // update after compress for all sub-pools
        Pool<? extends T, T> p = this;

        do {
            // update data
            p.data = d;

            // update structural knowledge of data
            p.staticDataInstances += p.newObjects.size() - p.deletedCount;
            p.deletedCount = 0;
            p.newObjects.clear();

            p.bpo = bpoMap[p.typeID - 10];

            p = p.next;
        } while (null != p);
    }
}
