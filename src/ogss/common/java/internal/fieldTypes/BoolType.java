package ogss.common.java.internal.fieldTypes;

import java.io.IOException;

import ogss.common.java.internal.FieldType;
import ogss.common.streams.InStream;
import ogss.common.streams.OutStream;

public final class BoolType extends FieldType<Boolean> {
    private static final BoolType instance = new BoolType();

    /**
     * @see ???
     */
    public static final int typeID = 0;

    public static BoolType get() {
        return instance;
    }

    private BoolType() {
        super(typeID);
    }

    @Override
    public Boolean r(InStream in) {
        return in.bool();
    }

    @Override
    public void w(Boolean target, OutStream out) throws IOException {
        out.bool(null != target && target);
    }

    @Override
    public String toString() {
        return "bool";
    }
}
