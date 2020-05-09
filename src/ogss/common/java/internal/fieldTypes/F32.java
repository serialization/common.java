package ogss.common.java.internal.fieldTypes;

import java.io.IOException;

import ogss.common.jvm.streams.InStream;
import ogss.common.jvm.streams.OutStream;

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
    public boolean w(Float target, OutStream out) throws IOException {
        float v = null == target ? 0 : target;
        out.f32(v);
        return 0 == v;
    }

    @Override
    public String toString() {
        return "f32";
    }
}
