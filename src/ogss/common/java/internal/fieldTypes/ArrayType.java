package ogss.common.java.internal.fieldTypes;

import java.io.IOException;
import java.util.ArrayList;

import ogss.common.java.internal.FieldType;
import ogss.common.jvm.streams.BufferedOutStream;
import ogss.common.jvm.streams.MappedInStream;

public final class ArrayType<T> extends SingleArgumentType<ArrayList<T>, T> {

    public ArrayType(int typeID, FieldType<T> base) {
        super(typeID, base);
    }

    @Override
    public String name() {
        return base + "[]";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ArrayType<?>)
            return base.equals(((ArrayType<?>) obj).base);
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
                idMap.set(++i, new ArrayList<>());

            return block;
        }
        // else, no blocks
        while (count-- != 0)
            idMap.add(new ArrayList<>());
        return 0;
    }

    @Override
    protected final void read(int i, final int end, MappedInStream in) throws IOException {
        while (i < end) {
            ArrayList<T> xs = idMap.get(++i);
            int s = in.v32();
            while (s-- != 0) {
                xs.add(base.r(in));
            }
        }
    }

    @Override
    protected final void write(int i, final int end, BufferedOutStream out) throws IOException {
        while (i < end) {
            ArrayList<T> xs = idMap.get(++i);
            out.v64(xs.size());
            for (T x : xs) {
                base.w(x, out);
            }
        }
    }
}
