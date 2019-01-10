package ogss.common.java.internal.fieldTypes;

import java.io.IOException;

import ogss.common.streams.InStream;
import ogss.common.streams.OutStream;

public final class F32 extends FloatType<Float> {

    /**
     * @see SKilL V1.0 reference manual §G
     */
    public static final int typeID = 6;

    private final static F32 instance = new F32();

    public static F32 get() {
        return instance;
    }

    private F32() {
        super(typeID);
    }

    @Override
    public Float r(InStream in) {
        return in.f32();
    }

    @Override
    public void w(Float target, OutStream out) throws IOException {
        out.f32(null == target ? 0 : target);
    }

    @Override
    public String toString() {
        return "f32";
    }
}
