package ogss.common.java.internal;

import java.io.IOException;

import ogss.common.java.internal.fieldTypes.MapType;
import ogss.common.java.internal.fieldTypes.SingleArgumentType;
import ogss.common.streams.BufferedOutStream;

/**
 * A job that writes a hull type to a buffer.
 * 
 * @author Timm Felden
 */
final class WHT extends WJob {
    private final HullType<?> t;
    /**
     * the block this task is responsible for; the task processing block 0 starts the other tasks and can therefore know
     * that it is not just a task that has to process its block
     */
    private int block;

    WHT(Writer self, HullType<?> t) {
        super(self);
        this.t = t;
    }

    @Override
    protected void job(BufferedOutStream buffer) throws IOException {
        buffer.v64(t.fieldID);

        final boolean hasblocks;

        // iff we have blockID zero we may need to split
        if (0 == block) {
            // split non-HS blocks that are too large into blocks
            if (t.typeID != StringPool.typeID && t.idMap.size() >= HullType.HD_Threshold) {
                hasblocks = true;
                // we have to fork this task
                int blockCount = t.idMap.size() / HullType.HD_Threshold;
                // @note we increment await by blockCount - 1
                synchronized (self) {
                    self.awaitBuffers += blockCount++;
                }

                t.blocks = blockCount;
                for (int i = 1; i < blockCount; i++) {
                    WHT job = new WHT(self, t);
                    job.block = i;
                    State.pool.execute(job);
                }
            } else {
                hasblocks = false;
            }
        } else {
            hasblocks = true;
        }

        discard = t.write(block, buffer);

        final boolean done;
        if (hasblocks) {
            synchronized (t) {
                done = 0 == --t.blocks;
            }
        } else {
            done = true;
        }

        if (done) {
            if (t instanceof SingleArgumentType<?, ?>) {
                SingleArgumentType<?, ?> p = (SingleArgumentType<?, ?>) t;
                if (p.base instanceof HullType<?>) {
                    HullType<?> t = (HullType<?>) p.base;
                    synchronized (t) {
                        if (0 == --t.deps) {
                            // execute task in this thread to avoid unnecessary overhead
                            tail = new WHT(self, t);
                        }
                    }
                }
            } else if (t instanceof MapType<?, ?>) {
                MapType<?, ?> p = (MapType<?, ?>) t;
                if (p.keyType instanceof HullType<?>) {
                    HullType<?> t = (HullType<?>) p.keyType;
                    synchronized (t) {
                        if (0 == --t.deps) {
                            tail = new WHT(self, t);
                        }
                    }
                }
                if (p.valueType instanceof HullType<?>) {
                    HullType<?> t = (HullType<?>) p.valueType;
                    synchronized (t) {
                        if (0 == --t.deps) {
                            if (null != tail) {
                                // the key hull has to be executed in parallel
                                State.pool.execute(tail);
                            }
                            // execute task in this thread to avoid unnecessary overhead
                            tail = new WHT(self, t);
                        }
                    }
                }
            }
        }
    }
}