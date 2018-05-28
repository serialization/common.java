package de.ust.skill.common.java.internal.fieldTypes;

import java.util.ArrayList;

import de.ust.skill.common.java.internal.FieldType;
import de.ust.skill.common.jvm.streams.InStream;

public final class VariableLengthArray<T> extends SingleArgumentType<ArrayList<T>, T> {

    /**
     * @see SKilL V1.0 reference manual §G
     */
    public static final int typeID = 17;

    public VariableLengthArray(FieldType<T> groundType) {
        super(typeID, groundType);
    }

    @Override
    public ArrayList<T> readSingleField(InStream in) {
        int i = in.v32();
        ArrayList<T> rval = new ArrayList<>(i);
        while (i-- != 0)
            rval.add(groundType.readSingleField(in));
        return rval;
    }

    @Override
    public String toString() {
        return groundType.toString() + "[]";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof VariableLengthArray<?>)
            return groundType.equals(((VariableLengthArray<?>) obj).groundType);
        return false;
    }
}
