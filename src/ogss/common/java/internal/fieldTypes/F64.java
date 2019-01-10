package ogss.common.java.internal.fieldTypes;

import java.io.IOException;

import ogss.common.streams.InStream;
import ogss.common.streams.OutStream;

public final class F64 extends FloatType<Double> {

    /**
     * @see SKilL V1.0 reference manual §G
     */
    public static final int typeID = 7;

    private final static F64 instance = new F64();

    public static F64 get() {
        return instance;
    }

    private F64() {
        super(typeID);
    }

    @Override
    public Double r(InStream in) {
        return in.f64();
    }

    @Override
    public void w(Double target, OutStream out) throws IOException {
        out.f64(null == target ? 0 : target);
    }

    @Override
    public String toString() {
        return "f64";
    }
}
