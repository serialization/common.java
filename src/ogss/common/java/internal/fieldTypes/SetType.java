package ogss.common.java.internal.fieldTypes;

import java.util.HashSet;

import ogss.common.java.internal.FieldType;
import ogss.common.streams.InStream;

public final class SetType<T> extends SingleArgumentType<HashSet<T>, T> {

    /**
     * @see SKilL V1.0 reference manual §G
     */
    public static final int typeID = 19;

    public SetType(FieldType<T> groundType) {
        super(typeID, groundType);
    }

    @Override
    public HashSet<T> r(InStream in) {
        // TODO incorrect

        int i = in.v32();
        HashSet<T> rval = new HashSet<>(1 + (i * 3) / 2);
        while (i-- != 0)
            rval.add(groundType.r(in));
        return rval;
    }

    @Override
    public String toString() {
        return "set<" + groundType.toString() + ">";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SetType<?>)
            return groundType.equals(((SetType<?>) obj).groundType);
        return false;
    }
}
