package ogss.common.java.internal;

/**
 * A job that performs the (former) compress operation on a base pool.
 * 
 * @author Timm Felden
 */
final class WCompress implements Runnable {

    final private Writer self;
    final private Pool<?> base;
    final private int[] bpos;

    WCompress(Writer self, Pool<?> p, int[] bpos) {
        this.self = self;
        this.base = p;
        this.bpos = bpos;
    }

    /**
     * compress new instances into the data array and update object IDs
     */
    @Override
    public void run() {

        // create our part of the bpo map
        {
            int next = 0;
            Pool<?> p = base;

            do {
                bpos[p.typeID - 10] = next;
                final int s = p.staticSize() - p.deletedCount;
                p.cachedSize = s;
                next += s;
                p = p.next;
            } while (null != p);
        }

        // calculate correct dynamic size for all sub pools
        {
            Pool<?>[] cs = base.owner.classes;
            for (int i = cs.length;;) {
                --i;
                Pool<?> p = cs[i];
                if (base == p)
                    break;
                if (base == p.basePool) {
                    p.superPool.cachedSize += p.cachedSize;
                }
            }
        }

        // reset layout of distributed fields
        {
            Pool<?> p = base;
            while (null != p) {
                for (FieldDeclaration<?, ?> f : p.dataFields) {
                    if (f instanceof DistributedField<?, ?>) {
                        ((DistributedField) f).compress(bpos[p.typeID - 10]);
                    }
                }
                p = p.next;
            }
        }

        // note: we could move the object update to updateAfterCompress and
        // perform that in parallel (because it is much easier without append)

        // from now on, size will take deleted objects into account, thus d may
        // in fact be smaller then data!
        Obj[] d = new Obj[base.cachedSize];
        int pos = 0;
        TypeOrderIterator<?> is = new TypeOrderIterator<>(base);
        while (is.hasNext()) {
            final Obj i = is.next();
            if (i.ID != 0) {
                d[pos++] = i;
                i.ID = pos;
            }
        }

        // update after compress for all sub-pools
        Pool<?> p = base;

        do {
            // update data
            p.data = d;

            // update structural knowledge of data
            p.staticDataInstances += p.newObjects.size() - p.deletedCount;
            p.deletedCount = 0;
            p.newObjects.clear();

            p.bpo = bpos[p.typeID - 10];

            p = p.next;
        } while (null != p);

        self.barrier.release();
    }
}