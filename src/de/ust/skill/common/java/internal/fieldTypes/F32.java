package de.ust.skill.common.java.internal.fieldTypes;

import java.io.IOException;
import java.util.Collection;

import de.ust.skill.common.jvm.streams.InStream;
import de.ust.skill.common.jvm.streams.OutStream;

public final class F32 extends FloatType<Float> {

    /**
     * @see SKilL V1.0 reference manual §G
     */
    public static final int typeID = 12;

    private final static F32 instance = new F32();

    public static F32 get() {
        return instance;
    }

    private F32() {
        super(typeID);
    }

    @Override
    public Float readSingleField(InStream in) {
        return in.f32();
    }

    @Override
    public long calculateOffset(Collection<Float> xs) {
        return 4 * xs.size();
    }

    @Override
    public long singleOffset(Float x) {
        return 4L;
    }

    @Override
    public void writeSingleField(Float target, OutStream out) throws IOException {
        out.f32(null == target ? 0 : target);
    }

    @Override
    public String toString() {
        return "f32";
    }
}
