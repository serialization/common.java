package de.ust.skill.common.java.internal.fieldTypes;

import java.io.IOException;
import java.util.Collection;

import de.ust.skill.common.java.internal.parts.Block;
import de.ust.skill.common.jvm.streams.InStream;
import de.ust.skill.common.jvm.streams.OutStream;

public final class I8 extends IntegerType<Byte> {
    private final static I8 instance = new I8();

    public static I8 get() {
        return instance;
    }

    private I8() {
        super(7);
    }

    @Override
    public Byte readSingleField(InStream in) {
        return in.i8();
    }

    @Override
    public long calculateOffset(Collection<Byte> xs, Block range) {
        return range.count;
    }

    @Override
    public void writeSingleField(Byte target, OutStream out) throws IOException {
        out.i8(target);
    }

    @Override
    public String toString() {
        return "i8";
    }
}
