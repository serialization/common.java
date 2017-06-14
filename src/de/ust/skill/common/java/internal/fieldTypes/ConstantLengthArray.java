package de.ust.skill.common.java.internal.fieldTypes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import de.ust.skill.common.java.internal.FieldType;
import de.ust.skill.common.jvm.streams.InStream;
import de.ust.skill.common.jvm.streams.OutStream;

public final class ConstantLengthArray<T> extends SingleArgumentType<ArrayList<T>, T> {
    public final int length;

    public ConstantLengthArray(long length, FieldType<T> groundType) {
        super(15, groundType);
        this.length = (int) length;
    }

    @Override
    public ArrayList<T> readSingleField(InStream in) {
        ArrayList<T> rval = new ArrayList<T>(length);
        for (int i = length; i-- != 0;)
            rval.add(groundType.readSingleField(in));
        return rval;
    }

    @Override
    public long calculateOffset(Collection<ArrayList<T>> xs) {
        long result = 0L;
        for (ArrayList<T> x : xs) {
            result += groundType.calculateOffset(x);
        }

        return result;
    }

    @Override
    public long singleOffset(ArrayList<T> xs) {
        return groundType.calculateOffset(xs);
    }

    @Override
    public void writeSingleField(ArrayList<T> elements, OutStream out) throws IOException {
        for (T e : elements)
            groundType.writeSingleField(e, out);
    }

    @Override
    public String toString() {
        return groundType.toString() + "[" + length + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ConstantLengthArray<?>)
            return length == ((ConstantLengthArray<?>) obj).length
                    && groundType.equals(((ConstantLengthArray<?>) obj).groundType);
        return false;
    }
}
