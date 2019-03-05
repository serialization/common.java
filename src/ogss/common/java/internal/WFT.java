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

    WFT(Writer self, FieldDeclaration<?, ?> f) {
        super(self);
        this.f = f;
    }

    @Override
    protected void job(BufferedOutStream buffer) throws IOException {
        Pool<?, ?> owner = f.owner;
        int i = owner.bpo;
        int h = i + owner.cachedSize;

        // any empty field will be discarded
        if (i != h) {
            buffer.v64(f.id);
            discard = f.write(i, h, buffer);
        }

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