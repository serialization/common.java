package ogss.common.java.internal.fieldTypes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import ogss.common.java.internal.ByRefType;
import ogss.common.java.internal.FieldType;
import ogss.common.java.internal.Pointer;
import ogss.common.java.internal.Pool;
import ogss.common.java.internal.State;
import ogss.common.java.internal.StringPool;
import ogss.common.streams.InStream;
import ogss.common.streams.OutStream;

/**
 * AnyRef types are instantiated once per state.
 * 
 * @author Timm Felden
 */
public final class AnyRefType extends ByRefType<Object> {

    /**
     * @see ???
     */
    public static final int typeID = 8;

    private final ArrayList<Pool<?, ?>> types;

    private final HashMap<String, ByRefType<?>> typeByName;

    /**
     * @param types
     *            the array list containing all types valid inside of a state
     * @note types can grow after passing the pointer to the annotation type. This behavior is required in order to
     *       implement reflective annotation parsing correctly.
     * @note can not take a state as argument, because it may not exist yet
     */
    public AnyRefType(ArrayList<Pool<?, ?>> types, HashMap<String, ByRefType<?>> typeByName) {
        super(typeID);
        this.types = types;
        this.typeByName = typeByName;
    }

    @Override
    public Object r(InStream in) {
        final int t = in.v32();
        if (0 == t)
            return null;

        final int f = in.v32();
        if (1 == t)
            return typeByName.get("string").get(f);

        // TODO fix this!
        return types.get(t - 1).get(f);
    }

    @Override
    public boolean w(Object ref, OutStream out) throws IOException {
        if (null == ref) {
            // magic trick!
            out.i8((byte) 0);
            return true;
        }

        if (ref instanceof Pointer) {
            out.v64(typeByName.get(((Pointer) ref).typeName()).typeID() - typeID);
            out.v64(((Pointer) ref).ID());
        } else if (ref instanceof String) {
            out.i8((byte) 1);
            out.v64(((StringPool) typeByName.get("string")).id((String) ref));
        } else {
            throw new Error("TODO");
        }
        return false;
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

    @Override
    public String name() {
        return "anyRef";
    }

    @Override
    public int size() {
        throw new Error("TODO");
    }

    @Override
    public State owner() {
        throw new Error("TODO");
    }

    @Override
    public Iterator<Object> iterator() {
        throw new Error("TODO");
    }

    @Override
    public Object get(int ID) {
        throw new NoSuchMethodError();
    }
}
