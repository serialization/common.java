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

    WHT(Writer self, HullType<?> t) {
        super(self);
        this.t = t;
    }

    @Override
    protected void job(BufferedOutStream buffer) throws IOException {
        buffer.v64(t.fieldID);
        discard = t.write(buffer);

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