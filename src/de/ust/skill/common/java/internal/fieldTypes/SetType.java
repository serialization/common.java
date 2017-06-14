package de.ust.skill.common.java.internal.fieldTypes;

import java.util.HashSet;

import de.ust.skill.common.java.internal.FieldType;
import de.ust.skill.common.jvm.streams.InStream;

public final class SetType<T> extends SingleArgumentType<HashSet<T>, T> {

    public SetType(FieldType<T> groundType) {
        super(19, groundType);
    }

    @Override
    public HashSet<T> readSingleField(InStream in) {
        int i = (int) in.v64();
        HashSet<T> rval = new HashSet<>(1 + (i * 3) / 2);
        while (i-- != 0)
            rval.add(groundType.readSingleField(in));
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
