package ogss.common.java.internal;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;

import ogss.common.java.internal.fieldDeclarations.AutoField;
import ogss.common.java.restrictions.FieldRestriction;
import ogss.common.streams.BufferedOutStream;
import ogss.common.streams.MappedInStream;

/**
 * Actual implementation as used by all bindings.
 * 
 * @author Timm Felden
 */
abstract public class FieldDeclaration<T, Obj extends Pointer> extends ogss.common.java.api.FieldDeclaration<T> {

    /**
     * @note types may change during file parsing. this may seem like a hack, but it makes file parser implementation a
     *       lot easier, because there is no need for two mostly similar type hierarchy implementations
     */
    protected FieldType<T> type;

    @Override
    public final FieldType<T> type() {
        return type;
    }

    /**
     * skill name of this
     */
    final String name;

    @Override
    public String name() {
        return name;
    }

    /**
     * the fieldID as used during serialization
     */
    final int id;

    /**
     * the enclosing storage pool
     */
    protected final Pool<Obj, ? super Obj> owner;

    @Override
    public Pool<Obj, ? super Obj> owner() {
        return owner;
    }

    /**
     * Restriction handling.
     */
    public final HashSet<FieldRestriction<T>> restrictions = new HashSet<>();

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addRestriction(HashSet<FieldRestriction<?>> r) {
        restrictions.addAll((HashSet) r);
    }

    /**
     * Check consistency of restrictions on this field.
     */
    final void check() {
        if (this instanceof LazyField) {
            ((LazyField<?, ?>) this).ensureLoaded();
        }

        if (!restrictions.isEmpty())
            for (Obj x : owner)
                if (!x.isDeleted())
                    for (FieldRestriction<T> r : restrictions)
                        r.check(get(x));
    }

    protected FieldDeclaration(FieldType<T> type, String name, int id, Pool<Obj, ? super Obj> owner) {
        this.type = type;
        this.name = name.intern(); // we will switch on names, thus we need to
                                   // intern them
        this.owner = owner;
        this.id = id;

        // auto fields get per-type negative IDs
        if (id < 0)
            owner.autoFields[-1 - id] = (AutoField<?, Obj>) this;
        else
            owner.dataFields.add(this);
    }

    @Override
    public String toString() {
        return type.toString() + " " + name;
    }

    /**
     * Field declarations are equal, iff their names and types are equal.
     * 
     * @note This makes fields of unequal enclosing types equal!
     */
    @Override
    public final boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj instanceof FieldDeclaration) {
            return ((FieldDeclaration<?, ?>) obj).name().equals(name)
                    && ((FieldDeclaration<?, ?>) obj).type.equals(type);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return type.hashCode() ^ name.hashCode();
    }

    /**
     * Read data from a mapped input stream and set it accordingly. This is invoked at the very end of state
     * construction and done massively in parallel.
     */
    protected abstract void read(int i, final int last, MappedInStream in);

    /**
     * write data into a map at the end of a write/append operation
     * 
     * @note this will always write the last chunk, as, in contrast to read, it is impossible to write to fields in
     *       parallel
     * @note only called, if there actually is field data to be written
     */
    protected abstract void write(int i, final int last, BufferedOutStream out) throws IOException;

    /**
     * punch a hole into the java type system :)
     */
    @SuppressWarnings("unchecked")
    protected static final <T, U> FieldType<T> cast(FieldType<U> f) {
        return (FieldType<T>) f;
    }

    /**
     * punch a hole into the java type system that eases implementation of maps of interfaces
     * 
     * @note the hole is only necessary, because interfaces cannot inherit from classes
     */
    @SuppressWarnings("unchecked")
    protected static final <K1, V1, K2, V2> HashMap<K1, V1> castMap(HashMap<K2, V2> arg) {
        return (HashMap<K1, V1>) arg;
    }
}
