package de.ust.skill.common.java.internal.fieldTypes;

import java.util.HashSet;

import de.ust.skill.common.java.internal.FieldType;
import de.ust.skill.common.jvm.streams.InStream;

public final class SetType<T> extends IntegerType<HashSet<T>> {
    public final FieldType<T> groundType;

    public SetType(FieldType<T> groundType) {
        super(19);
        this.groundType = groundType;
    }

    @Override
    public HashSet<T> readSingleField(InStream in) {
        HashSet<T> rval = new HashSet<>();
        for (int i = (int) in.v64(); i != 0; i--)
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