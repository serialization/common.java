package de.ust.skill.common.java.internal.fieldTypes;

import java.io.IOException;
import java.util.Collection;

import de.ust.skill.common.java.internal.FieldType;
import de.ust.skill.common.jvm.streams.OutStream;

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

    @Override
    public long calculateOffset(Collection<T> xs) {
        long result = 0L;
        for (T x : xs) {
            if (null == x)
                result += 1;
            else {
                result += V64.singleV64Offset(x.size());
                result += groundType.calculateOffset(x);
            }
        }

        return result;
    }

    @Override
    public long singleOffset(T x) {
        if (null == x)
            return 1L;

        return V64.singleV64Offset(x.size()) + groundType.calculateOffset(x);
    }

    @Override
    public void writeSingleField(T elements, OutStream out) throws IOException {
        if (null == elements) {
            out.i8((byte) 0);
        } else {
            out.v64(elements.size());
            for (Base e : elements)
                groundType.writeSingleField(e, out);
        }
    }
}
