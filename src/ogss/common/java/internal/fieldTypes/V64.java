package ogss.common.java.internal.fieldTypes;

import java.io.IOException;

import ogss.common.streams.InStream;
import ogss.common.streams.OutStream;

public final class V64 extends IntegerType<Long> {

    /**
     * @see SKilL V1.0 reference manual §G
     */
    public static final int typeID = 5;

    private final static V64 instance = new V64();

    public static V64 get() {
        return instance;
    }

    private V64() {
        super(typeID);
    }

    @Override
    public Long r(InStream in) {
        return in.v64();
    }

    @Override
    public boolean w(Long target, OutStream out) throws IOException {
        long v = null == target ? 0 : target;
        out.v64(v);
        return 0 == v;
    }

    @Override
    public String toString() {
        return "v64";
    }
}
