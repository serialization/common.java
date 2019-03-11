package ogss.common.java.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

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

    private final ArrayList<Pool<?>> types;

    State owner;

    /**
     * @param types
     *            the array list containing all types valid inside of a state
     * @note types can grow after passing the pointer to the annotation type. This behavior is required in order to
     *       implement reflective annotation parsing correctly.
     * @note can not take a state as argument, because it may not exist yet
     */
    public AnyRefType(ArrayList<Pool<?>> types) {
        super(typeID);
        this.types = types;
    }

    @Override
    public Object r(InStream in) {
        final int t = in.v32();
        if (0 == t)
            return null;

        final int f = in.v32();
        if (1 == t)
            return owner.Strings().get(f);

        // TODO fix this!
        return types.get(t - 2).get(f);
    }

    @Override
    public boolean w(Object ref, OutStream out) throws IOException {
        if (null == ref) {
            // magic trick!
            out.i8((byte) 0);
            return true;
        }

        if (ref instanceof Obj) {
            out.v64(owner.pool(((Obj) ref).typeName()).typeID() - typeID);
            out.v64(((Obj) ref).ID());
        } else if (ref instanceof String) {
            out.i8((byte) 1);
            out.v64(owner.strings.id((String) ref));
        } else {
            throw new Error("TODO");
        }
        return false;
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
        return owner;
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
