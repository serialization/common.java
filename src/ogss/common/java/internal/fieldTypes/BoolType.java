package ogss.common.java.internal.fieldTypes;

import java.io.IOException;

import ogss.common.java.internal.FieldType;
import ogss.common.jvm.streams.InStream;
import ogss.common.jvm.streams.MappedInStream;
import ogss.common.jvm.streams.OutStream;

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
        return ((MappedInStream) in).bool();
    }

    @Override
    public boolean w(Boolean target, OutStream out) throws IOException {
        throw new NoSuchMethodError("the caller has to wrap out!");
    }

    @Override
    public String toString() {
        return "bool";
    }
}
