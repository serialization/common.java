package ogss.common.java.internal.fieldTypes;

import java.util.LinkedList;

import ogss.common.java.internal.FieldType;
import ogss.common.streams.InStream;

public final class ListType<T> extends SingleArgumentType<LinkedList<T>, T> {

    /**
     * @see SKilL V1.0 reference manual §G
     */
    public static final int typeID = 18;

    public ListType(FieldType<T> groundType) {
        super(typeID, groundType);
    }

    @Override
    public LinkedList<T> r(InStream in) {
        // TODO not correct: value vs. ref!
        LinkedList<T> rval = new LinkedList<>();
        for (int i = in.v32(); i != 0; i--)
            rval.add(groundType.r(in));
        return rval;
    }

    @Override
    public String toString() {
        return "list<" + groundType.toString() + ">";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ListType<?>)
            return groundType.equals(((ListType<?>) obj).groundType);
        return false;
    }
}
