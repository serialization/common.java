package ogss.common.java.internal.fieldTypes;

import java.io.IOException;
import java.util.HashSet;

import ogss.common.java.internal.FieldType;
import ogss.common.streams.BufferedOutStream;
import ogss.common.streams.MappedInStream;

public final class SetType<T> extends SingleArgumentType<HashSet<T>, T> {

    public SetType(int typeID, FieldType<T> groundType) {
        super(typeID, groundType);
    }

    @Override
    public String name() {
        return "set<" + base.toString() + ">";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SetType<?>)
            return base.equals(((SetType<?>) obj).base);
        return false;
    }

    @Override
    protected int allocateInstances(int count, MappedInStream in) {
        // check for blocks
        if (count > HD_Threshold) {
            final int block = in.v32();
            // initialize idMap with null to allow parallel updates
            synchronized (this) {
                if (1 == idMap.size()) {
                    int c = count;
                    while (c-- != 0)
                        idMap.add(null);
                }
            }
            int i = block * HD_Threshold;
            final int end = Math.min(count, i + HD_Threshold);
            while (i < end)
                idMap.set(++i, new HashSet<>());

            return block;
        }
        // else, no blocks
        while (count-- != 0)
            idMap.add(new HashSet<>());
        return 0;
    }

    @Override
    protected final void read(int i, final int end, MappedInStream in) throws IOException {
        while (i < end) {
            HashSet<T> xs = idMap.get(++i);
            int s = in.v32();
            while (s-- != 0) {
                xs.add(base.r(in));
            }
        }
    }

    @Override
    protected final void write(int i, final int end, BufferedOutStream out) throws IOException {
        while (i < end) {
            HashSet<T> xs = idMap.get(++i);
            out.v64(xs.size());
            for (T x : xs) {
                base.w(x, out);
            }
        }
    }
}
