package ogss.common.java.internal.fieldTypes;

import java.io.IOException;
import java.util.Collection;

import ogss.common.java.internal.FieldType;
import ogss.common.streams.OutStream;

/**
 * Super class of all container types with one type argument.
 * 
 * @author Timm Felden
 */
public abstract class SingleArgumentType<T extends Collection<Base>, Base> extends CompoundType<T> {
    public final FieldType<Base> groundType;

    public SingleArgumentType(int typeID, FieldType<Base> groundType) {
        super(typeID);
        this.groundType = groundType;
    }

    @SuppressWarnings("null")
    @Override
    public void w(T x, OutStream out) throws IOException {
        // TODO incorrect
        final int size = null == x ? 0 : x.size();
        if (0 == size) {
            out.i8((byte) 0);
        } else {
            out.v64(size);
            for (Base e : x)
                groundType.w(e, out);
        }
    }
}
