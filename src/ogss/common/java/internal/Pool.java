package ogss.common.java.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import ogss.common.java.api.Access;
import ogss.common.java.api.SkillException;
import ogss.common.java.internal.fieldDeclarations.AutoField;
import ogss.common.java.internal.fieldTypes.ReferenceType;
import ogss.common.streams.InStream;
import ogss.common.streams.OutStream;

/**
 * Top level implementation of all storage pools.
 * 
 * @author Timm Felden
 * @param <T>
 *            static type of instances
 * @param <B>
 *            base type of this hierarchy
 * @note Storage pools must be created in type order!
 * @note We do not guarantee functional correctness if instances from multiple skill files are mixed. Such usage will
 *       likely break at least one of the files.
 */
public class Pool<T extends B, B extends Pointer> extends FieldType<T> implements Access<T>, ReferenceType {

    /**
     * Builder for new instances of the pool.
     * 
     * @author Timm Felden
     * @todo revisit implementation after the pool is completely implemented. Having an instance as constructor argument
     *       is questionable.
     */
    protected static abstract class Builder<T extends Pointer> {
        protected Pool<T, ? super T> pool;
        protected T instance;

        protected Builder(Pool<T, ? super T> pool, T instance) {
            this.pool = pool;
            this.instance = instance;
        }

        /**
         * registers the object and invalidates the builder
         * 
         * @note abstract to work around JVM bug
         * @return the created object
         */
        abstract public T make();
    }

    final String name;

    // type hierarchy
    public final Pool<? super T, B> superPool;
    public final int typeHierarchyHeight;

    public final BasePool<B> basePool;

    /**
     * Find a super pool if only its name is known. This is called by generated constructors to allow correct
     * instantiation of types from the tool specification
     */
    @SuppressWarnings("unchecked")
    protected static <T extends B, B extends Pointer> Pool<? super T, B> findSuperPool(ArrayList<Pool<?, ?>> types,
            String name) {
        int i = types.size();
        while (--i >= 0) {
            Pool<?, ?> r = types.get(i);
            if (r.name == name)
                return (Pool<? super T, B>) r;
        }
        throw new Error("internal error");
    }

    /**
     * the next pool; used for efficient type hierarchy traversal.
     * 
     * @note on setting nextPool, the bpo of nextPool will be adjusted iff it is 0 to allow insertion of types from the
     *       tool specification
     */
    Pool<?, B> next;

    /**
     * @return next pool of this hierarchy in weak type order
     */
    public Pool<?, B> nextPool() {
        return next;
    }

    /**
     * pointer to base-pool-managed data array
     */
    protected Pointer[] data;

    /**
     * names of known fields, the actual field information is given in the generated addKnownFiled method.
     */
    public final String[] knownFields;
    public static final String[] noKnownFields = new String[0];

    /**
     * Classes of known fields used to allocate them
     */
    public final Class<KnownDataField<?, T>>[] KFC;
    @SuppressWarnings("unchecked")
    public static final Class<KnownDataField<?, ?>>[] noKFC = new Class[0];

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
    protected static final AutoField<?, ?>[] noAutoFields = new AutoField<?, ?>[0];

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

    protected final DynamicNewInstancesIterator<T, B> newDynamicInstances() {
        return new DynamicNewInstancesIterator<>(this);
    }

    protected final int newDynamicInstancesSize() {
        int rval = 0;
        TypeHierarchyIterator<T, B> ts = new TypeHierarchyIterator<>(this);
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

    /***
     * @note cast required to work around weakened type system by javac 1.8.131
     */
    @Override
    @SuppressWarnings("unchecked")
    public final StaticDataIterator<T> staticInstances() {
        return (StaticDataIterator<T>) new StaticDataIterator<Pointer>((Pool<Pointer, Pointer>) this);
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
    final public Pool<? super T, B> superType() {
        return superPool;
    }

    /**
     * @note the unchecked cast is required, because we can not supply this as an argument in a super constructor, thus
     *       the base pool can not be an argument to the constructor. The cast will never fail anyway.
     */
    @SuppressWarnings("unchecked")
    protected Pool(int poolIndex, String name, Pool<? super T, B> superPool, String[] knownFields,
            Class<KnownDataField<?, T>>[] KFC, AutoField<?, T>[] autoFields) {
        super(10 + poolIndex);
        this.name = name.intern();

        this.superPool = superPool;
        if (null == superPool) {
            this.typeHierarchyHeight = 0;
            this.basePool = (BasePool<B>) this;
        } else {
            this.typeHierarchyHeight = superPool.typeHierarchyHeight + 1;
            this.basePool = superPool.basePool;
        }
        this.knownFields = knownFields;
        this.KFC = KFC;
        dataFields = new ArrayList<>(knownFields.length);
        this.autoFields = autoFields;
    }

    /**
     * @return the instance matching argument object id
     */
    @SuppressWarnings("unchecked")
    final public T getByID(int ID) {
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
    public final void w(T ref, OutStream out) throws IOException {
        if (null == ref)
            out.i8((byte) 0);
        else
            out.v64(ref.ID);
    }

    /**
     * @return size including subtypes
     */
    @Override
    final public int size() {
        int size = 0;
        TypeHierarchyIterator<T, B> ts = new TypeHierarchyIterator<>(this);
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
        DynamicDataIterator<T, B> is = iterator();
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
    final void delete(Pointer target) {
        if (!target.isDeleted()) {
            target.ID = 0;
            deletedCount++;
        }
    }

    @Override
    public State owner() {
        return basePool.owner();
    }

    @Override
    final public DynamicDataIterator<T, B> iterator() {
        return new DynamicDataIterator<T, B>(this);
    }

    @Override
    final public TypeOrderIterator<T, B> typeOrderIterator() {
        return new TypeOrderIterator<T, B>(this);
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
        int i = bpo;
        final int high = i + staticDataInstances;
        while (i < high) {
            data[i] = new Pointer.SubType(this, i + 1);
            i += 1;
        }
    }

    /**
     * used internally for type forest construction
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Pool<? extends T, B> makeSubPool(int index, String name) {
        // @note cannot solve type equation without turning noKFC into a function
        return new Pool(index, name, this, noKnownFields, noKFC, noAutoFields);
    }

    @Override
    final public String toString() {
        return name;
    }
}
