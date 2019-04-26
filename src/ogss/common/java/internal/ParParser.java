package ogss.common.java.internal;

import java.nio.BufferUnderflowException;
import java.util.concurrent.Semaphore;

import ogss.common.java.api.OGSSException;
import ogss.common.java.internal.exceptions.PoolSizeMissmatchError;
import ogss.common.streams.FileInputStream;
import ogss.common.streams.MappedInStream;

/**
 * The parallel version of Parser.
 *
 * @author Timm Felden
 */
public final class ParParser extends Parser {

    // synchronization of field read jobs
    Semaphore barrier;

    OGSSException readErrors;

    ParParser(FileInputStream in, PoolBuilder pb) {
        super(in, pb);
    }

    /**
     * parse T and F
     */
    @Override
    final void typeBlock() {
        // init barrier, because we are not allowed to do so in the constructor :-/
        barrier = new Semaphore(0);

        /**
         * *************** * T Class * ****************
         */
        typeDefinitions();

        // calculate cached size and next for all pools
        {
            final int cs = classes.size();
            if (0 != cs) {
                int i = cs - 2;
                if (i >= 0) {
                    Pool<?> n, p = classes.get(i + 1);
                    // propagate information in reverse order
                    // i is the pool where next is set, hence we skip the last pool
                    do {
                        n = p;
                        p = classes.get(i);

                        // by compactness, if n has a super pool, p is the previous pool
                        if (null != n.superPool) {
                            n.superPool.cachedSize += n.cachedSize;
                        }

                    } while (--i >= 0);
                }

                // allocate data and start instance allocation jobs
                Obj[] d = null;
                while (++i < cs) {
                    final Pool<?> p = classes.get(i);
                    if (null == p.superPool) {
                        // create new d, because we are in a new type hierarchy
                        d = new Obj[p.cachedSize];
                    }
                    p.data = d;
                    if (0 != p.staticDataInstances) {
                        State.pool.execute(new Runnable() {
                            @Override
                            public void run() {
                                p.allocateInstances();
                                barrier.release();
                            }
                        });
                    } else {
                        // we would not allocate an instance anyway
                        barrier.release();
                    }
                }
            }
        }

        /**
         * *************** * T Container * ****************
         */
        TContainer();

        /**
         * *************** * T Enum * ****************
         */
        TEnum();

        /**
         * *************** * F * ****************
         */
        for (Pool<?> p : classes) {
            readFields(p);
        }
    }

    /**
     * Jump through HD-entries to create read tasks
     */
    @Override
    final void processData() {

        // we expect one HD-entry per field
        int remaining = fields.size();
        Runnable[] jobs = new Runnable[remaining];

        int awaitHulls = 0;

        while (--remaining >= 0 & !in.eof()) {
            // create the map directly and use it for subsequent read-operations to avoid costly position and size
            // readjustments
            final MappedInStream map = in.map(in.v32() + 2);

            final int id = map.v32();
            final Object f = fields.get(id);
            
            // TODO add a countermeasure against duplicate buckets / fieldIDs

            if (f instanceof HullType<?>) {
                final int count = map.v32();
                final HullType<?> p = (HullType<?>) f;

                // start hull allocation job
                awaitHulls++;
                State.pool.execute(new Runnable() {
                    @Override
                    public void run() {
                        int block = p.allocateInstances(count, map);

                        // create hull read data task except for StringPool which is still lazy per element and eager
                        // per offset
                        if (!(p instanceof StringPool)) {
                            jobs[id] = new HRT(p, block, map);
                        }

                        barrier.release();
                    }
                });

            } else {
                // create job with adjusted size that corresponds to the * in the specification (i.e. exactly the data)
                jobs[id] = new ReadTask((FieldDeclaration<?, ?>) f, map);
            }
        }

        // await allocations of class and hull types
        try {
            barrier.acquire(classes.size() + awaitHulls);
        } catch (InterruptedException e) {
            throw new OGSSException("internal error: unexpected foreign exception", e);
        }

        // start read tasks
        {
            int skips = 0;
            for (Runnable j : jobs) {
                if (null != j)
                    State.pool.execute(j);
                else
                    skips++;
            }
            if (0 != skips)
                barrier.release(skips);
        }

        // TODO start tasks that perform default initialization of fields not obtained from file
    }

    @Override
    public void awaitResults() {
        // await read jobs and throw error if any occurred
        try {
            barrier.acquire(fields.size());
        } catch (InterruptedException e) {
            throw new OGSSException("internal error: unexpected foreign exception", e);
        }
        if (null != readErrors)
            throw readErrors;
    }

    private final class ReadTask implements Runnable {
        private final FieldDeclaration<?, ?> f;
        private final MappedInStream map;

        ReadTask(FieldDeclaration<?, ?> f, MappedInStream in) {
            this.f = f;
            this.map = in;
        }

        @Override
        public void run() {
            OGSSException ex = null;
            final Pool<?> owner = f.owner;
            final int bpo = owner.bpo;
            final int end = bpo + owner.cachedSize;
            try {
                if (map.eof()) {
                    // TODO default initialization; this is a nop for now in Java
                } else {
                    f.read(bpo, end, map);
                }

                if (!map.eof() && !(f instanceof LazyField<?, ?>))
                    ex = new PoolSizeMissmatchError(map.position(), bpo, end, f);

            } catch (BufferUnderflowException e) {
                ex = new PoolSizeMissmatchError(bpo, end, f, e);
            } catch (OGSSException t) {
                ex = t;
            } catch (Throwable t) {
                ex = new OGSSException("internal error: unexpected foreign exception", t);
            } finally {
                barrier.release();
                if (null != ex)
                    synchronized (fields) {
                        if (null == readErrors)
                            readErrors = ex;
                        else
                            readErrors.addSuppressed(ex);
                    }
            }
        }
    }

    /**
     * A hull read task. Reads H-Data.
     * 
     * @author Timm Felden
     */
    private final class HRT implements Runnable {
        private final HullType<?> t;
        private final int block;
        private final MappedInStream map;

        HRT(HullType<?> t, int block, MappedInStream map) {
            this.t = t;
            this.block = block;
            this.map = map;
        }

        @Override
        public void run() {
            OGSSException ex = null;
            try {
                t.read(block, map);
            } catch (OGSSException t) {
                ex = t;
            } catch (Throwable t) {
                ex = new OGSSException("internal error: unexpected foreign exception", t);
            } finally {
                barrier.release();
                if (null != ex)
                    synchronized (fields) {
                        if (null == readErrors)
                            readErrors = ex;
                        else
                            readErrors.addSuppressed(ex);
                    }
            }
        }
    }
}
