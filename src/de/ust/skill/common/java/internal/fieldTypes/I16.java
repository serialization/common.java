package de.ust.skill.common.java.internal.fieldTypes;

import java.io.IOException;
import java.util.Collection;

import de.ust.skill.common.jvm.streams.InStream;
import de.ust.skill.common.jvm.streams.OutStream;

public final class I16 extends IntegerType<Short> {

    /**
     * @see SKilL V1.0 reference manual §G
     */
    public static final int typeID = 8;

    private final static I16 instance = new I16();

    public static I16 get() {
        return instance;
    }

    private I16() {
        super(typeID);
    }

    @Override
    public Short readSingleField(InStream in) {
        return in.i16();
    }

    @Override
    public long calculateOffset(Collection<Short> xs) {
        return 2 * xs.size();
    }

    @Override
    public long singleOffset(Short x) {
        return 2L;
    }

    @Override
    public void writeSingleField(Short target, OutStream out) throws IOException {
        out.i16(target);
    }

    @Override
    public String toString() {
        return "i16";
    }
}
