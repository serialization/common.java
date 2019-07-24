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
    private final HullType<?> ht;
    /**
     * the block this task is responsible for; the task processing block 0 starts the other tasks and can therefore know
     * that it is not just a task that has to process its block
     */
    private int block;

    WHT(Writer self, HullType<?> t) {
        super(self);
        this.ht = t;
    }

    @Override
    protected void job(BufferedOutStream buffer) throws IOException {
        if (ht instanceof ContainerType<?>) {
            final ContainerType<?> t = (ContainerType<?>) ht;

            boolean hasblocks = false;

            final int size = t.IDs.size();

            if (!(discard = (0 == size))) {
                // iff we have blockID zero we may need to split
                if (0 == block) {
                    // split non-HS blocks that are too large into blocks
                    if (t.typeID != StringPool.typeID && size > HullType.HD_Threshold) {
                        hasblocks = true;
                        // we have to fork this task
                        int blockCount = (size - 1) / HullType.HD_Threshold;
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
                    }
                } else {
                    hasblocks = true;
                }

                buffer.v64(t.fieldID);
                buffer.v64(size);
                if (size > HullType.HD_Threshold) {
                    buffer.v64(block);
                }
                int i = block * HullType.HD_Threshold;
                final int end = Math.min(size, i + HullType.HD_Threshold);
                t.write(i, end, buffer);
            }

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
                        HullType<?> bt = (HullType<?>) p.base;
                        synchronized (bt) {
                            if (0 == --bt.deps) {
                                // execute task in this thread to avoid unnecessary overhead
                                tail = new WHT(self, bt);
                            }
                        }
                    }
                } else if (t instanceof MapType<?, ?>) {
                    MapType<?, ?> p = (MapType<?, ?>) t;
                    if (p.keyType instanceof HullType<?>) {
                        HullType<?> bt = (HullType<?>) p.keyType;
                        synchronized (bt) {
                            if (0 == --bt.deps) {
                                tail = new WHT(self, bt);
                            }
                        }
                    }
                    if (p.valueType instanceof HullType<?>) {
                        HullType<?> bt = (HullType<?>) p.valueType;
                        synchronized (bt) {
                            if (0 == --bt.deps) {
                                if (null != tail) {
                                    // the key hull has to be executed in parallel
                                    State.pool.execute(tail);
                                }
                                // execute task in this thread to avoid unnecessary overhead
                                tail = new WHT(self, bt);
                            }
                        }
                    }
                }
            }
        } else {
            if (ht.idMap.size() != 0) {
                discard = ((StringPool) ht).write(buffer);
            }
        }
    }
}