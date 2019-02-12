package ogss.common.java.internal.fieldTypes;

import java.util.ArrayList;

import ogss.common.java.internal.FieldType;
import ogss.common.streams.MappedInStream;

public final class ArrayType<T> extends SingleArgumentType<ArrayList<T>, T> {

    public ArrayType(int typeID, FieldType<T> base) {
        super(typeID, base);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ArrayType<?>)
            return base.equals(((ArrayType<?>) obj).base);
        return false;
    }

    @Override
    public String name() {
        return base + "[]";
    }

    @Override
    protected void allocateInstances(int count, MappedInStream in) {
        this.in = in;
        while (count-- != 0)
            idMap.add(new ArrayList<>());
    }
}
