package de.ust.skill.common.java.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import de.ust.skill.common.java.api.Access;
import de.ust.skill.common.java.api.SkillException;
import de.ust.skill.common.java.internal.fieldDeclarations.AutoField;
import de.ust.skill.common.java.internal.fieldTypes.Annotation;
import de.ust.skill.common.java.internal.fieldTypes.ReferenceType;
import de.ust.skill.common.java.internal.fieldTypes.StringType;
import de.ust.skill.common.java.internal.parts.Block;
import de.ust.skill.common.java.internal.parts.BulkChunk;
import de.ust.skill.common.java.internal.parts.Chunk;
import de.ust.skill.common.java.internal.parts.SimpleChunk;
import de.ust.skill.common.java.iterators.Iterators;
import de.ust.skill.common.java.restrictions.FieldRestriction;
import de.ust.skill.common.jvm.streams.InStream;
import de.ust.skill.common.jvm.streams.OutStream;

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
abstract public class StoragePool<T extends B, B extends SkillObject> extends FieldType<T>
        implements Access<T>, ReferenceType {

    /**
     * Builder for new instances of the pool.
     * 
     * @author Timm Felden
     * @todo revisit implementation after the pool is completely implemented. Having an instance as constructor argument
     *       is questionable.
     */
    protected static abstract class Builder<T> {
        protected StoragePool<T, ? super T> pool;
        protected T instance;

        protected Builder(StoragePool<T, ? super T> pool, T instance) {
            this.pool = pool;
            this.instance = instance;
        }

        public T make() {
            pool.add(instance);
            return instance;
        }
    }

    final String name;

    // type hierarchy
    final StoragePool<? super T, B> superPool;
    protected final BasePool<B> basePool;
    final ArrayList<SubPool<? extends T, B>> subPools = new ArrayList<>();

    /**
     * used by generated file parsers
     */
    public StoragePool<? super T, B> superPool() {
        return superPool;
    }

    /**
     * used by generated file parsers / known fields
     */
    public BasePool<B> basePool() {
        return basePool;
    }

    /**
     * names of known fields, the actual field information is given in the generated addKnownFiled method.
     */
    public final Set<String> knownFields;

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
    static final AutoField<?, ?>[] noAutoFields = new AutoField<?, ?>[0];

    /**
     * @return magic cast to placeholder which well never fail at runtime, because the array is empty anyway
     */
    @SuppressWarnings("unchecked")
    protected static final <T extends SkillObject> AutoField<?, T>[] noAutoFields() {
        return (AutoField<?, T>[]) noAutoFields;
    }

    /**
     * @return an iterator over all auto fields
     * @note O(T)
     */
    Iterator<FieldDeclaration<?, T>> allAutoFields() {
        return Iterators.array(autoFields);
    }

    /**
     * all fields that hold actual data
     * 
     * @note stores fields at index "f.index-1"
     */
    protected final ArrayList<FieldDeclaration<?, T>> dataFields;

    /**
     * The block layout of instances of this pool.
     */
    final LinkedList<Block> blocks = new LinkedList<>();

    protected LinkedList<Block> blocks() {
        return blocks;
    }

    /**
     * internal use only!
     */
    public Block lastBlock() {
        return blocks.getLast();
    }

    /**
     * All stored objects, which have exactly the type T. Objects are stored as arrays of field entries. The types of
     * the respective fields can be retrieved using the fieldTypes map.
     */
    final ArrayList<T> newObjects = new ArrayList<>();

    /**
     * Ensures that at least capacity many new objects can be stored in this pool without moving references.
     */
    public void hintNewObjectsSize(int capacity) {
        newObjects.ensureCapacity(capacity);
    }

    protected final Iterator<T> newDynamicInstances() {
        LinkedList<Iterator<? extends T>> is = new LinkedList<>();
        if (!newObjects.isEmpty())
            is.add(newObjects.iterator());
        for (SubPool<? extends T, B> sub : subPools) {
            Iterator<? extends T> subIter = sub.newDynamicInstances();
            if (subIter.hasNext())
                is.add(subIter);
        }
        return Iterators.<T> concatenate(is);
    }

    protected final int newDynamicInstancesSize() {
        int rval = newObjects.size();
        for (SubPool<? extends T, B> sub : subPools) {
            rval += sub.newDynamicInstancesSize();
        }
        return rval;
    }

    /**
     * the number of instances of exactly this type, excluding sub-types
     * 
     * @return size excluding subtypes
     */
    final public int staticSize() {
        return staticData.size() + newObjects.size();
    }

    final Iterator<T> staticInstances() {
        return Iterators.<T> concatenate(staticData.iterator(), newObjects.iterator());
    }

    /**
     * the number of static instances loaded from the file
     */
    final protected ArrayList<T> staticData = new ArrayList<>();

    /**
     * storage pools can be fixed, i.e. no dynamic instances can be added to the pool. Fixing a pool requires that it
     * does not contain a new object. Fixing a pool will fix subpools as well. Un-fixing a pool will un-fix super pools
     * as well, thus being fixed is a transitive property over the sub pool relation. Pools will be fixed by flush
     * operations.
     */
    boolean fixed = false;
    /**
     * size that is only valid in fixed state
     */
    int cachedSize;

    /**
     * number of deleted objects in this state
     */
    protected int deletedCount = 0;

    /**
     * !!internal use only!!
     */
    public final boolean fixed() {
        return fixed;
    }

    /**
     * set new fixation status; if setting fails, some sub pools may have been fixed nonetheless.
     * 
     * @note this may change the result of size(), because from now on, the deleted objects will be taken into account
     */
    public final void fixed(boolean newStatus) {
        if (fixed == newStatus)
            return;

        if (newStatus) {
            for (SubPool<?, B> s : subPools)
                s.fixed(true);

            // take deletions into account
            cachedSize = size() - deletedCount;

        } else {
            if (null != superPool)
                superPool.fixed(false);
        }
        fixed = newStatus;
    }

    @Override
    final public String name() {
        return name;
    }

    @Override
    public String superName() {
        if (null != superPool)
            return superPool.name;
        return null;
    }

    /**
     * @note the unchecked cast is required, because we can not supply this as an argument in a super constructor, thus
     *       the base pool can not be an argument to the constructor. The cast will never fail anyway.
     */
    @SuppressWarnings("unchecked")
    StoragePool(int poolIndex, String name, StoragePool<? super T, B> superPool, Set<String> knownFields,
            AutoField<?, T>[] autoFields) {
        super(32 + poolIndex);
        this.name = name;
        this.superPool = superPool;
        this.basePool = null == superPool ? (BasePool<B>) this : superPool.basePool;
        this.knownFields = knownFields;
        dataFields = new ArrayList<>(knownFields.size());
        this.autoFields = autoFields;
    }

    /**
     * @return the instance matching argument skill id
     */
    public abstract T getByID(long index);

    @Override
    public final T readSingleField(InStream in) {
        return getByID(in.v64());
    }

    @Override
    public final long calculateOffset(Collection<T> xs) {
        // shortcut small compressed types
        if (basePool.data.length < 128)
            return xs.size();

        long result = 0L;
        for (T x : xs) {
            long v = x.skillID;
            if (0L == (v & 0xFFFFFFFFFFFFFF80L)) {
                result += 1;
            } else if (0L == (v & 0xFFFFFFFFFFFFC000L)) {
                result += 2;
            } else if (0L == (v & 0xFFFFFFFFFFE00000L)) {
                result += 3;
            } else if (0L == (v & 0xFFFFFFFFF0000000L)) {
                result += 4;
            } else if (0L == (v & 0xFFFFFFF800000000L)) {
                result += 5;
            } else if (0L == (v & 0xFFFFFC0000000000L)) {
                result += 6;
            } else if (0L == (v & 0xFFFE000000000000L)) {
                result += 7;
            } else if (0L == (v & 0xFF00000000000000L)) {
                result += 8;
            } else {
                result += 9;
            }
        }
        return result;
    }

    @Override
    public final void writeSingleField(T ref, OutStream out) throws IOException {
        out.v64(null == ref ? 0 : ref.skillID);
    }

    /**
     * @return size including subtypes
     */
    @Override
    final public int size() {
        if (fixed)
            return cachedSize;

        int size = staticSize();
        for (SubPool<?, ?> s : subPools)
            size += s.size();
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size() != 0;
    }

    @Override
    public boolean contains(Object o) {
        throw new Error("TODO");
    }

    @Override
    public Object[] toArray() {
        throw new Error("TODO");
    }

    @Override
    public <U> U[] toArray(U[] a) {
        throw new Error("TODO");
    }

    @Override
    public final boolean add(T e) {
        if (fixed)
            throw new IllegalStateException("can not fix a pool that contains new objects");

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
    final void delete(SkillObject target) {
        if (!target.isDeleted()) {
            target.skillID = 0;
            deletedCount++;
        }
    }

    @Override
    public boolean remove(Object o) {
        if (o instanceof SkillObject) {
            owner().delete((SkillObject) o);
            return true;
        }
        return false;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object i : c)
            if (!contains(i))
                return false;
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        boolean changed = false;
        for (T i : c)
            changed |= add(i);
        return changed;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean changed = false;
        for (Object i : c)
            changed |= remove(i);
        return changed;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        // TODO provide an implementation that works for single state usage
        // scenario
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        // TODO there are more efficient implementations then that
        removeAll(this);
    }

    @Override
    public SkillState owner() {
        return basePool.owner();
    }

    @Override
    final public Iterator<T> typeOrderIterator() {
        ArrayList<Iterator<? extends T>> is = new ArrayList<>(subPools.size() + 1);
        is.add(staticInstances());
        for (SubPool<? extends T, B> s : subPools)
            is.add(s.typeOrderIterator());

        return Iterators.concatenate(is);
    }

    @Override
    public Iterator<FieldDeclaration<?, T>> fields() {
        return Iterators.<FieldDeclaration<?, T>> concatenate(Iterators.array(autoFields), dataFields.iterator());
    }

    @Override
    public T make() throws SkillException {
        throw new SkillException("We prevent reflective creation of new instances, because it is bad style!");
    }

    /**
     * insert new T instances with default values based on the last block info
     */
    abstract void insertInstances();

    protected final void updateAfterCompress(int[] lbpoMap) {
        blocks.clear();
        blocks.add(new Block(lbpoMap[typeID - 32], size()));
        staticData.addAll(newObjects);
        newObjects.clear();
        newObjects.trimToSize();
        for (SubPool<?, ?> p : subPools)
            p.updateAfterCompress(lbpoMap);
    }

    /**
     * internal use only! adds an unknown field
     */
    public <R> FieldDeclaration<R, T> addField(int ID, FieldType<R> type, String name,
            HashSet<FieldRestriction<?>> restrictions) {
        FieldDeclaration<R, T> f = new LazyField<R, T>(type, name, ID, this);
        for (FieldRestriction<?> r : restrictions)
            f.addRestriction(r);
        dataFields.add(f);
        return f;
    }

    /**
     * used internally for state allocation
     */
    @SuppressWarnings("static-method")
    public void addKnownField(String name, StringType string, Annotation annotation) {
        throw new Error("Arbitrary storage pools know no fields!");
    }

    /**
     * used internally for type forest construction
     */
    public StoragePool<? extends T, B> makeSubPool(int index, String name) {
        return new SubPool<>(index, name, this, Collections.emptySet(), noAutoFields());
    }

    /**
     * called after a prepare append operation to write empty the new objects buffer and to set blocks correctly
     */
    protected final void updateAfterPrepareAppend(Map<FieldDeclaration<?, ?>, Chunk> chunkMap) {
        final boolean newInstances = newDynamicInstances().hasNext();
        final boolean newPool = blocks.isEmpty();
        final boolean newField;
        {
            boolean exists = false;
            for (FieldDeclaration<?, T> f : dataFields) {
                if (f.noDataChunk()) {
                    exists = true;
                    break;
                }
            }

            newField = exists;
        }

        if (newPool || newInstances || newField) {

            // build block chunk
            final int lcount = newDynamicInstancesSize();
            // //@ note this is the index into the data array and NOT the written lbpo
            final int lbpo = (0 == lcount) ? 0 : ((int) newDynamicInstances().next().skillID - 1);

            blocks.addLast(new Block(lbpo, lcount));

            // @note: if this does not hold for p; then it will not hold for p.subPools either!
            if (newInstances || !newPool) {
                // build field chunks
                for (FieldDeclaration<?, T> f : dataFields) {
                    if (0 == f.index)
                        continue;

                    final Chunk c;
                    if (f.noDataChunk()) {
                        c = new BulkChunk(-1, -1, size());
                    } else if (newInstances) {
                        c = new SimpleChunk(-1, -1, lbpo, lcount);
                    } else
                        continue;

                    f.addChunk(c);
                    chunkMap.put(f, c);
                }
            }
        }
        // notify sub pools
        for (SubPool<?, B> p : subPools)
            p.updateAfterPrepareAppend(chunkMap);

        // remove new objects, because they are regular objects by now
        staticData.addAll(newObjects);
        newObjects.clear();
        newObjects.trimToSize();
    }

    @Override
    final public String toString() {
        return name;
    }
}
