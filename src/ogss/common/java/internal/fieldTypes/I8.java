package ogss.common.java.internal.fieldTypes;

import java.io.IOException;

import ogss.common.streams.InStream;
import ogss.common.streams.OutStream;

public final class I8 extends IntegerType<Byte> {

    /**
     * @see SKilL V1.0 reference manual §G
     */
    public static final int typeID = 1;

    private final static I8 instance = new I8();

    public static I8 get() {
        return instance;
    }

    private I8() {
        super(typeID);
    }

    @Override
    public Byte r(InStream in) {
        return in.i8();
    }
    @Override
    public void w(Byte target, OutStream out) throws IOException {
        out.i8(null == target ? 0 : target.byteValue());
    }

    @Override
    public String toString() {
        return "i8";
    }
}
