package ogss.common.java.internal.fieldTypes;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

import ogss.common.java.internal.FieldType;
import ogss.common.java.internal.HullType;
import ogss.common.streams.BufferedOutStream;
import ogss.common.streams.MappedInStream;

/**
 * Super class of all container types with one type argument.
 * 
 * @author Timm Felden
 */
public abstract class SingleArgumentType<T extends Collection<Base>, Base> extends HullType<T> {
    public final FieldType<Base> base;

    public SingleArgumentType(int typeID, FieldType<Base> base) {
        super(typeID);
        this.base = base;
    }

    @Override
    public int size() {
        return IDs.size();
    }

    @Override
    public Iterator<T> iterator() {
        return IDs.keySet().iterator();
    }

    @Override
    public final T get(int ID) {
        return idMap.get(ID);
    }

    protected MappedInStream in;

    @Override
    protected final void read() {
        final int count = idMap.size() - 1;
        for (int i = 1; i <= count; i++) {
            T xs = idMap.get(i);
            int s = in.v32();
            while (s-- != 0) {
                xs.add(base.r(in));
            }
        }
    }

    @Override
    protected final boolean write(BufferedOutStream out) throws IOException {
        final int count = idMap.size() - 1;
        if (0 != count) {
            out.v64(count);
            for (int i = 1; i <= count; i++) {
                T xs = idMap.get(i);
                out.v64(xs.size());
                for (Base x : xs) {
                    base.w(x, out);
                }
            }
            return false;
        }
        return true;
    }
}
