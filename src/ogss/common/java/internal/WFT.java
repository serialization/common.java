package ogss.common.java.internal;

import java.io.IOException;

import ogss.common.streams.BufferedOutStream;

/**
 * A job that writes field data to a buffer.
 * 
 * @author Timm Felden
 */
final class WFT extends WJob {
    private final FieldDeclaration<?, ?> f;
    /**
     * the bucket this task is responsible for; the task processing bucket 0 starts the other tasks and can therefore
     * know that it is not just a task that has to process its bucket
     */
    private int bucket;

    WFT(Writer self, FieldDeclaration<?, ?> f) {
        super(self);
        this.f = f;
    }

    @Override
    protected void job(BufferedOutStream buffer) throws IOException {

        final int count = f.owner.cachedSize;

        final boolean hasBuckets;

        // any empty field will be discarded
        if (count != 0) {

            // iff we have bucketID zero we may need to split
            if (0 == bucket) {
                // split large FD blocks into buckets
                if (count >= FieldDeclaration.FD_Threshold) {
                    hasBuckets = true;

                    // we have to fork this task
                    int bucketCount = count / FieldDeclaration.FD_Threshold;
                    // @note we increment await by bucketCount - 1
                    synchronized (self) {
                        self.awaitBuffers += bucketCount++;
                    }

                    f.buckets = bucketCount;
                    for (int i = 1; i < bucketCount; i++) {
                        WFT job = new WFT(self, f);
                        job.bucket = i;
                        State.pool.execute(job);
                    }
                } else {
                    hasBuckets = false;
                }
            } else {
                hasBuckets = true;
            }

            Pool<?> owner = f.owner;
            final int bpo = owner.bpo;
            int i = bucket * FieldDeclaration.FD_Threshold;
            int h = Math.min(count, i + FieldDeclaration.FD_Threshold);
            i += bpo;
            h += bpo;

            buffer.v64(f.id);
            if (count >= FieldDeclaration.FD_Threshold) {
                buffer.v64(bucket);
            }
            discard = f.write(i, h, buffer);

        } else {
            hasBuckets = false;
        }

        final boolean done;
        if (hasBuckets) {
            synchronized (f) {
                done = 0 == --f.buckets;
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