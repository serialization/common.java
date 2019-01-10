package ogss.common.java.internal.fieldTypes;

import java.io.IOException;

import ogss.common.streams.InStream;
import ogss.common.streams.OutStream;

public final class I16 extends IntegerType<Short> {

    /**
     * @see SKilL V1.0 reference manual §G
     */
    public static final int typeID = 2;

    private final static I16 instance = new I16();

    public static I16 get() {
        return instance;
    }

    private I16() {
        super(typeID);
    }

    @Override
    public Short r(InStream in) {
        return in.i16();
    }

    @Override
    public void w(Short target, OutStream out) throws IOException {
        out.i16(null == target ? 0 : target);
    }

    @Override
    public String toString() {
        return "i16";
    }
}
