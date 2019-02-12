package ogss.common.java.internal.fieldTypes;

import java.util.LinkedList;

import ogss.common.java.internal.FieldType;
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
    protected void allocateInstances(int count, MappedInStream in) {
        this.in = in;
        while (count-- != 0)
            idMap.add(new LinkedList<>());

    }
}
