package ogss.common.java.internal.fieldTypes;

import java.io.IOException;

import ogss.common.streams.InStream;
import ogss.common.streams.OutStream;

public final class I32 extends IntegerType<Integer> {

    /**
     * @see SKilL V1.0 reference manual §G
     */
    public static final int typeID = 3;

    private final static I32 instance = new I32();

    public static I32 get() {
        return instance;
    }

    private I32() {
        super(typeID);
    }

    @Override
    public Integer r(InStream in) {
        return in.i32();
    }

    @Override
    public void w(Integer target, OutStream out) throws IOException {
        out.i32(null == target ? 0 : target);
    }

    @Override
    public String toString() {
        return "i32";
    }
}
