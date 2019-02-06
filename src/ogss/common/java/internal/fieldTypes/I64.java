package ogss.common.java.internal.fieldTypes;

import java.io.IOException;

import ogss.common.streams.InStream;
import ogss.common.streams.OutStream;

public final class I64 extends IntegerType<Long> {

    /**
     * @see SKilL V1.0 reference manual §G
     */
    public static final int typeID = 4;

    private final static I64 instance = new I64();

    public static I64 get() {
        return instance;
    }

    private I64() {
        super(typeID);
    }

    @Override
    public Long r(InStream in) {
        return in.i64();
    }

    @Override
    public void w(Long target, OutStream out) throws IOException {
        out.i64(null == target ? 0 : target);
    }

    @Override
    public String toString() {
        return "i64";
    }
}