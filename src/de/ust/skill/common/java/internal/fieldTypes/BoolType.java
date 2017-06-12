package de.ust.skill.common.java.internal.fieldTypes;

import java.io.IOException;
import java.util.Collection;

import de.ust.skill.common.java.internal.FieldType;
import de.ust.skill.common.jvm.streams.InStream;
import de.ust.skill.common.jvm.streams.OutStream;

public final class BoolType extends FieldType<Boolean> {
    private static final BoolType instance = new BoolType();

    public static BoolType get() {
        return instance;
    }

    private BoolType() {
        super(6);
    }

    @Override
    public Boolean readSingleField(InStream in) {
        return in.bool();
    }

    @Override
    public long calculateOffset(Collection<Boolean> xs) {
        return xs.size();
    }

    @Override
    public long singleOffset(Boolean x) {
        return 1L;
    }

    @Override
    public void writeSingleField(Boolean data, OutStream out) throws IOException {
        out.bool(data);
    }

    @Override
    public String toString() {
        return "bool";
    }
}
