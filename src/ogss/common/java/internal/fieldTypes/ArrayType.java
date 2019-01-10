package ogss.common.java.internal.fieldTypes;

import java.util.ArrayList;

import ogss.common.java.internal.FieldType;
import ogss.common.streams.InStream;

public final class ArrayType<T> extends SingleArgumentType<ArrayList<T>, T> {

    /**
     * @see SKilL V1.0 reference manual §G
     */
    public static final int typeID = 17;

    public ArrayType(FieldType<T> groundType) {
        super(typeID, groundType);
    }

    @Override
    public ArrayList<T> r(InStream in) {
        // TODO incorrect
        int i = in.v32();
        ArrayList<T> rval = new ArrayList<>(i);
        while (i-- != 0)
            rval.add(groundType.r(in));
        return rval;
    }

    @Override
    public String toString() {
        return groundType.toString() + "[]";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ArrayType<?>)
            return groundType.equals(((ArrayType<?>) obj).groundType);
        return false;
    }
}
