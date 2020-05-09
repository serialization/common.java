package ogss.common.java.internal;

import java.io.IOException;

import ogss.common.jvm.streams.BufferedOutStream;

/**
 * A job that writes field data to a buffer.
 * 
 * @author Timm Felden
 */
final class WFT extends WJob {
    private final FieldDeclaration<?, ?> f;
    /**
     * the block this task is responsible for; the task processing block 0 starts the other tasks and can therefore know
     * that it is not just a task that has to process its block
     */
    private int block;

    WFT(Writer self, FieldDeclaration<?, ?> f) {
        super(self);
        this.f = f;
    }

    @Override
    protected void job(BufferedOutStream buffer) throws IOException {

        final int size = f.owner.cachedSize;

        final boolean hasblocks;

        // any empty field will be discarded
        if (size != 0) {

            // iff we have blockID zero we may need to split
            if (0 == block) {
                // split large FD blocks into blocks
                if (size > FieldDeclaration.FD_Threshold) {
                    hasblocks = true;

                    // we have to fork this task
                    int blockCount = (size - 1) / FieldDeclaration.FD_Threshold;
                    // @note we increment await by blockCount - 1
                    synchronized (self) {
                        self.awaitBuffers += blockCount++;
                    }

                    f.blocks = blockCount;
                    for (int i = 1; i < blockCount; i++) {
                        WFT job = new WFT(self, f);
                        job.block = i;
                        State.pool.execute(job);
                    }
                } else {
                    hasblocks = false;
                }
            } else {
                hasblocks = true;
            }

            Pool<?> owner = f.owner;
            final int bpo = owner.bpo;
            int i = block * FieldDeclaration.FD_Threshold;
            int h = Math.min(size, i + FieldDeclaration.FD_Threshold);
            i += bpo;
            h += bpo;

            buffer.v64(self.FFID[f.id]);
            if (size > FieldDeclaration.FD_Threshold) {
                buffer.v64(block);
            }
            discard = f.write(i, h, buffer);

        } else {
            hasblocks = false;
        }

        final boolean done;
        if (hasblocks) {
            synchronized (f) {
                done = 0 == --f.blocks;
            }
        } else {
            done = true;
        }

        if (done) {
            if (f.type instanceof HullType<?>) {
                HullType<?> t = (HullType<?>) f.type;
                synchronized (t) {
                    if (0 == --t.deps) {
                        // execute task in this thread to avoid unnecessary overhead
                        tail = new WHT(self, t);
                    }
                }
            }
        }
    }
}