package ogss.common.java.internal.fieldTypes;

import java.util.HashSet;

import ogss.common.java.internal.FieldType;
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
    protected void allocateInstances(int count, MappedInStream in) {
        this.in = in;
        while (count-- != 0)
            idMap.add(new HashSet<>());
    }
}
