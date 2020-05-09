package ogss.common.java.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import ogss.common.jvm.streams.InStream;
import ogss.common.jvm.streams.OutStream;

/**
 * AnyRef types are instantiated once per state.
 * 
 * @author Timm Felden
 */
public final class AnyRefType extends ByRefType<Obj> {

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
    public Obj r(InStream in) {
        final int t = in.v32();
        if (0 == t)
            return null;

        final int f = in.v32();

        return types.get(t - 1).get(f);
    }

    @Override
    public boolean w(Obj ref, OutStream out) throws IOException {
        if (null == ref) {
            // magic trick!
            out.i8((byte) 0);
            return true;
        }

        int stid = ref.stid();
        Pool<?> p = -1 != stid ? (Pool<?>) owner.SIFA[stid] : ((NamedObj) ref).τp();
        out.v64(p.typeID - 9);
        out.v64(ref.ID());

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
    public Iterator<Obj> iterator() {
        throw new Error("TODO");
    }

    @Override
    public Obj get(int ID) {
        throw new NoSuchMethodError();
    }
}
