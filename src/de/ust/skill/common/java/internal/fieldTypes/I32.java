package de.ust.skill.common.java.internal.fieldTypes;

import java.io.IOException;
import java.util.Collection;

import de.ust.skill.common.jvm.streams.InStream;
import de.ust.skill.common.jvm.streams.OutStream;

public final class I32 extends IntegerType<Integer> {

    /**
     * @see SKilL V1.0 reference manual §G
     */
    public static final int typeID = 9;

    private final static I32 instance = new I32();

    public static I32 get() {
        return instance;
    }

    private I32() {
        super(typeID);
    }

    @Override
    public Integer readSingleField(InStream in) {
        return in.i32();
    }

    @Override
    public long calculateOffset(Collection<Integer> xs) {
        return 4 * xs.size();
    }

    @Override
    public long singleOffset(Integer x) {
        return 4L;
    }

    @Override
    public void writeSingleField(Integer target, OutStream out) throws IOException {
        out.i32(target);
    }

    @Override
    public String toString() {
        return "i32";
    }
}
