package de.ust.skill.common.java.internal.fieldTypes;

import java.util.ArrayList;

import de.ust.skill.common.java.internal.FieldType;
import de.ust.skill.common.jvm.streams.InStream;

public final class VariableLengthArray<T> extends SingleArgumentType<ArrayList<T>, T> {

    public VariableLengthArray(FieldType<T> groundType) {
        super(17, groundType);
    }

    @Override
    public ArrayList<T> readSingleField(InStream in) {
        int i = (int) in.v64();
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
