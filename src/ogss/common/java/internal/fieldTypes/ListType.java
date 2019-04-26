package ogss.common.java.internal.fieldTypes;

import java.io.IOException;
import java.util.LinkedList;

import ogss.common.java.internal.FieldType;
import ogss.common.streams.BufferedOutStream;
import ogss.common.streams.MappedInStream;

public final class ListType<T> extends SingleArgumentType<LinkedList<T>, T> {

    public ListType(int typeID, FieldType<T> groundType) {
        super(typeID, groundType);
    }

    @Override
    public String name() {
        return "list<" + base.toString() + ">";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ListType<?>)
            return base.equals(((ListType<?>) obj).base);
        return false;
    }

    @Override
    protected int allocateInstances(int count, MappedInStream in) {
        // check for buckets
        if (count >= HD_Threshold) {
            final int bucket = in.v32();
            // initialize idMap with null to allow parallel updates
            synchronized (this) {
                if (1 == idMap.size()) {
                    int c = count;
                    while (c-- != 0)
                        idMap.add(null);
                }
            }
            int i = bucket * HD_Threshold;
            final int end = Math.min(count, i + HD_Threshold);
            while (i < end)
                idMap.set(++i, new LinkedList<>());

            return bucket;
        }
        // else, no buckets
        while (count-- != 0)
            idMap.add(new LinkedList<>());
        return 0;
    }

    @Override
    protected final void read(int bucket, MappedInStream in) {
        int i = bucket * HD_Threshold;
        final int end = Math.min(idMap.size(), i + HD_Threshold);
        while (++i < end) {
            LinkedList<T> xs = idMap.get(i);
            int s = in.v32();
            while (s-- != 0) {
                xs.add(base.r(in));
            }
        }
    }

    @Override
    protected final boolean write(int bucket, BufferedOutStream out) throws IOException {
        final int count = idMap.size() - 1;
        if (0 != count) {
            out.v64(count);
            if (count >= HD_Threshold) {
                out.v64(bucket);
            }
            int i = bucket * HD_Threshold;
            final int end = Math.min(idMap.size(), i + HD_Threshold);
            while (++i < end) {
                LinkedList<T> xs = idMap.get(i);
                out.v64(xs.size());
                for (T x : xs) {
                    base.w(x, out);
                }
            }
            return false;
        }
        return true;
    }
}
