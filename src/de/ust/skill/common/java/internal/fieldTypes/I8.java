package de.ust.skill.common.java.internal.fieldTypes;

import java.io.IOException;
import java.util.Collection;

import de.ust.skill.common.jvm.streams.InStream;
import de.ust.skill.common.jvm.streams.OutStream;

public final class I8 extends IntegerType<Byte> {

    /**
     * @see SKilL V1.0 reference manual §G
     */
    public static final int typeID = 7;

    private final static I8 instance = new I8();

    public static I8 get() {
        return instance;
    }

    private I8() {
        super(typeID);
    }

    @Override
    public Byte readSingleField(InStream in) {
        return in.i8();
    }

    @Override
    public long calculateOffset(Collection<Byte> xs) {
        return xs.size();
    }

    @Override
    public long singleOffset(Byte x) {
        return 1L;
    }

    @Override
    public void writeSingleField(Byte target, OutStream out) throws IOException {
        out.i8(null == target ? 0 : target.byteValue());
    }

    @Override
    public String toString() {
        return "i8";
    }
}
