package ogss.common.java.internal.fieldTypes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import ogss.common.java.internal.FieldType;
import ogss.common.java.internal.Pointer;
import ogss.common.java.internal.Pool;
import ogss.common.streams.InStream;
import ogss.common.streams.OutStream;

/**
 * AnyRef types are instantiated once per state.
 * 
 * @author Timm Felden
 */
public final class AnyRefType extends FieldType<Object> implements ReferenceType {

    /**
     * @see ???
     */
    public static final int typeID = 9;

    private final ArrayList<Pool<?, ?>> types;
    private HashMap<String, Pool<?, ?>> typeByName = null;

    /**
     * @param types
     *            the array list containing all types valid inside of a state
     * @note types can grow after passing the pointer to the annotation type. This behavior is required in order to
     *       implement reflective annotation parsing correctly.
     * @note can not take a state as argument, because it may not exist yet
     */
    public AnyRefType(ArrayList<Pool<?, ?>> types) {
        super(typeID);
        this.types = types;
        assert types != null;
    }

    public void fixTypes(HashMap<String, Pool<?, ?>> poolByName) {
        assert typeByName == null;
        typeByName = poolByName;
    }

    @Override
    public Pointer r(InStream in) {
        final int t = in.v32();
        if (0 == t)
            return null;

        final int f = in.v32();
        return types.get(t - 1).getByID(f);
    }

    @Override
    public void w(Object ref, OutStream out) throws IOException {
        if (null == ref) {
            // magic trick!
            out.i8((byte) 0);
            return;
        }

        if (ref instanceof Pointer)
            out.v64(typeByName.get(((Pointer) ref).typeName()).typeID() - 31);
        out.v64(((Pointer) ref).ID());

    }

    @Override
    public String toString() {
        return "anyRef";
    }

    /**
     * required for proper treatment of Interface types (because Java interfaces cannot inherit from classes)
     */
    public static <T> AnyRefType cast(FieldType<T> f) {
        return (AnyRefType) f;
    }
}
