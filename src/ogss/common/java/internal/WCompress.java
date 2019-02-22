package ogss.common.java.internal;

import java.util.ArrayList;

/**
 * A job that performs the (former) compress operation on a base pool.
 * 
 * @author Timm Felden
 */
final class WCompress implements Runnable {

    final private Writer self;
    final private BasePool<?> p;
    final private int[] bpos;

    WCompress(Writer self, BasePool<?> p, int[] bpos) {
        this.self = self;
        this.p = p;
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
            Pool<?, ?> q = p;

            do {
                bpos[q.typeID - 10] = next;
                final int s = q.staticSize() - q.deletedCount;
                q.cachedSize = s;
                next += s;
                q = q.next;
            } while (null != q);
        }

        // calculate correct dynamic size for all sub pools
        {
            ArrayList<Pool<?, ?>> cs = p.owner.classes;
            for (int i = cs.size();;) {
                --i;
                Pool<?, ?> q = cs.get(i);
                if (p == q)
                    break;
                if (p == q.basePool) {
                    q.superPool.cachedSize += q.cachedSize;
                }
            }
        }

        // note: we could move the object update to updateAfterCompress and
        // perform that in parallel (because it is much easier without append)

        // from now on, size will take deleted objects into account, thus d may
        // in fact be smaller then data!
        Pointer[] d = new Pointer[p.cachedSize];
        int pos = 0;
        TypeOrderIterator<?, ?> is = new TypeOrderIterator<>(p);
        while (is.hasNext()) {
            final Pointer i = is.next();
            if (i.ID != 0) {
                d[pos++] = i;
                i.ID = pos;
            }
        }

        // update after compress for all sub-pools
        Pool<?, ?> q = p;

        do {
            // update data
            q.data = d;

            // update structural knowledge of data
            q.staticDataInstances += q.newObjects.size() - q.deletedCount;
            q.deletedCount = 0;
            q.newObjects.clear();

            q.bpo = bpos[q.typeID - 10];

            q = q.next;
        } while (null != q);

        self.barrier.release();
    }
}