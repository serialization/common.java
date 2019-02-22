package ogss.common.java.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import ogss.common.java.api.Access;
import ogss.common.java.api.SkillException;
import ogss.common.java.internal.fieldDeclarations.AutoField;
import ogss.common.streams.InStream;
import ogss.common.streams.OutStream;

/**
 * Top level implementation of all storage pools.
 * 
 * @author Timm Felden
 * @param <T>
 *            static type of instances
 * @note Storage pools are created by a StateInitializer, only
 */
public class Pool<T extends Obj> extends ByRefType<T> implements Access<T> {

    public final String name;

    protected State owner = null;

    @Override
    public final State owner() {
        return owner;
    }

    // type hierarchy
    public final Pool<? super T> superPool, basePool;
    /**
     * the number of super pools aka the height of the type hierarchy
     */
    public final int THH;

    /**
     * Find a super pool if only its name is known. This is called by generated constructors to allow correct
     * instantiation of types from the tool specification
     */
    @SuppressWarnings("unchecked")
    protected static <T extends Obj> Pool<? super T> findSuperPool(ArrayList<Pool<?>> types, String name) {
        int i = types.size();
        while (--i >= 0) {
            Pool<?> r = types.get(i);
            if (r.name == name)
                return (Pool<? super T>) r;
        }
        throw new Error("internal error");
    }

    /**
     * the next pool; used for efficient type hierarchy traversal.
     * 
     * @note on setting nextPool, the bpo of nextPool will be adjusted iff it is 0 to allow insertion of types from the
     *       tool specification
     */
    Pool<?> next;

    /**
     * @return next pool of this hierarchy in weak type order
     */
    public Pool<?> next() {
        return next;
    }

    /**
     * pointer to base-pool-managed data array
     */
    protected Obj[] data;

    /**
     * names of known fields, the actual field information is given in the generated addKnownFiled method.
     */
    public final String[] KFN;
    public static final String[] myKFN = new String[0];

    /**
     * Classes of known fields used to allocate them
     */
    public final Class<FieldDeclaration<?, T>>[] KFC;

    /**
     * magic trick using static name resolution; KFC should be a class val, but that's not possible in java, so we use
     * static fields instead and pass them; if no KFC is specified, the empty pool myKFC wins resolution.
     */
    @SuppressWarnings("rawtypes")
    public static final Class[] myKFC = new Class[0];

    /**
     * all fields that are declared as auto, including skillID
     * 
     * @note stores fields at index "-f.index"
     * @note sub-constructor adds auto fields from super types to this array; this is an optimization to make iteration
     *       O(1); the array cannot change anyway
     * @note the initial type constructor will already allocate an array of the correct size, because the right size is
     *       statically known (a generation time constant)
     */
    protected final AutoField<?, T>[] autoFields;
    /**
     * used as placeholder, if there are no auto fields at all to optimize allocation time and memory usage
     */
    private static final AutoField<?, ?>[] noAutoFields = new AutoField<?, ?>[0];

    /**
     * all fields that hold actual data
     * 
     * @note stores fields at index "f.index-1"
     */
    protected final ArrayList<FieldDeclaration<?, T>> dataFields;

    @Override
    public StaticFieldIterator fields() {
        return new StaticFieldIterator(this);
    }

    @Override
    public FieldIterator allFields() {
        return new FieldIterator(this);
    }

    /**
     * The BPO of this pool relative to data.
     */
    protected int bpo;

    /**
     * All stored objects, which have exactly the type T. Objects are stored as arrays of field entries. The types of
     * the respective fields can be retrieved using the fieldTypes map.
     */
    final ArrayList<T> newObjects = new ArrayList<>();

    /**
     * retrieve a new object
     * 
     * @param index
     *            in [0;{@link #newObjectsSize()}[
     * @return the new object at the given position
     */
    public final T newObject(int index) {
        return newObjects.get(index);
    }

    /**
     * Ensures that at least capacity many new objects can be stored in this pool without moving references.
     */
    public final void hintNewObjectsSize(int capacity) {
        newObjects.ensureCapacity(capacity);
    }

    protected final DynamicNewInstancesIterator<T> newDynamicInstances() {
        return new DynamicNewInstancesIterator<>(this);
    }

    protected final int newDynamicInstancesSize() {
        int rval = 0;
        TypeHierarchyIterator<T> ts = new TypeHierarchyIterator<>(this);
        while (ts.hasNext())
            rval += ts.next().newObjects.size();

        return rval;
    }

    /**
     * Number of static instances of T in data. Managed by read/compress.
     */
    protected int staticDataInstances;

    /**
     * the number of instances of exactly this type, excluding sub-types
     * 
     * @return size excluding subtypes
     */
    final public int staticSize() {
        return staticDataInstances + newObjects.size();
    }

    @Override
    public final StaticDataIterator<T> staticInstances() {
        return new StaticDataIterator<>(this);
    }

    /**
     * size that is only valid in fixed state
     * 
     * @note in contrast to SKilL/Java, we maintain this as an internal invariant only!
     */
    int cachedSize;

    /**
     * number of deleted objects in this state
     */
    protected int deletedCount = 0;

    @Override
    final public String name() {
        return name;
    }

    @Override
    final public Pool<? super T> superType() {
        return superPool;
    }

    /**
     * @note the unchecked cast is required, because we can not supply this as an argument in a super constructor, thus
     *       the base pool can not be an argument to the constructor. The cast will never fail anyway.
     */
    @SuppressWarnings("unchecked")
    protected Pool(int poolIndex, String name, Pool<? super T> superPool, String[] knownFields,
            Class<FieldDeclaration<?, T>>[] KFC, int autoFields) {
        super(10 + poolIndex);
        this.name = name;

        this.superPool = superPool;
        if (null == superPool) {
            this.THH = 0;
            this.basePool = this;
        } else {
            this.THH = superPool.THH + 1;
            this.basePool = superPool.basePool;
        }
        this.KFN = knownFields;
        this.KFC = KFC;
        dataFields = new ArrayList<>(knownFields.length);
        this.autoFields = 0 == autoFields ? (AutoField[]) noAutoFields : new AutoField[autoFields];
    }

    /**
     * @return the instance matching argument object id
     */
    @Override
    @SuppressWarnings("unchecked")
    final public T get(int ID) {
        int index = ID - 1;
        if (index < 0 | data.length <= index)
            return null;
        return (T) data[index];
    }

    @SuppressWarnings("unchecked")
    @Override
    public final T r(InStream in) {
        int index = in.v32() - 1;
        if (index < 0 | data.length <= index)
            return null;
        return (T) data[index];
    }

    @Override
    public final boolean w(T ref, OutStream out) throws IOException {
        if (null == ref) {
            out.i8((byte) 0);
            return true;
        }

        out.v64(ref.ID);
        return false;
    }

    /**
     * @return size including subtypes
     */
    @Override
    final public int size() {
        int size = 0;
        TypeHierarchyIterator<T> ts = new TypeHierarchyIterator<>(this);
        while (ts.hasNext())
            size += ts.next().staticSize();
        return size;
    }

    @Override
    final public Stream<T> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    public T[] toArray(T[] a) {
        final T[] rval = Arrays.copyOf(a, size());
        DynamicDataIterator<T> is = iterator();
        for (int i = 0; i < rval.length; i++) {
            rval[i] = is.next();
        }
        return rval;
    }

    /**
     * Add an existing instance as a new objects.
     * 
     * @note Do not use objects managed by other skill files.
     */
    public final boolean add(T e) {
        e.ID = -1 - newObjects.size();
        return newObjects.add(e);
    }

    /**
     * Delete shall only be called from skill state
     * 
     * @param target
     *            the object to be deleted
     * @note we type target using the erasure directly, because the Java type system is too weak to express correct
     *       typing, when taking the pool from a map
     */
    final void delete(Obj target) {
        if (!target.isDeleted()) {
            target.ID = 0;
            deletedCount++;
        }
    }

    @Override
    final public DynamicDataIterator<T> iterator() {
        return new DynamicDataIterator<>(this);
    }

    @Override
    final public TypeOrderIterator<T> typeOrderIterator() {
        return new TypeOrderIterator<T>(this);
    }

    @Override
    public T make() throws SkillException {
        throw new SkillException("We prevent reflective creation of new instances, because it is bad style!");
    }

    /**
     * insert new T instances with default values based on the last block info
     * 
     * @note defaults to unknown objects to reduce code size
     */
    protected void allocateInstances() {
        int i = bpo, j;
        final int high = i + staticDataInstances;
        while (i < high) {
            data[i] = new UnknownObject(this, j = (i + 1));
            i = j;
        }
    }

    /**
     * used internally for type forest construction
     */
    @SuppressWarnings("unchecked")
    protected Pool<? extends T> makeSubPool(int index, String name) {
        return new Pool<>(index, name, this, myKFN, myKFC, 0);
    }
}
