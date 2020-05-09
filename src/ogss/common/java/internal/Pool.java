package ogss.common.java.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import ogss.common.java.api.Access;
import ogss.common.java.api.OGSSException;
import ogss.common.java.internal.fieldDeclarations.AutoField;
import ogss.common.jvm.streams.InStream;
import ogss.common.jvm.streams.OutStream;

/**
 * Top level implementation of all storage pools.
 * 
 * @author Timm Felden
 * @param <T>
 *            static type of instances
 * @note Pools are created by a StateInitializer, only
 */
public abstract class Pool<T extends Obj> extends ByRefType<T>
      implements Access<T> {

   public final String name;

   protected State owner;

   @Override
   public final State owner() {
      return owner;
   }

   /**
    * Allow generated pool implementations to overwrite OGSS IDs of objects
    * managed by them.
    */
   protected void setID(T ref, int ID) {
      ref.ID = ID;
   }

   // type hierarchy
   public final Pool<? super T> superPool, basePool;
   /**
    * the number of super pools aka the height of the type hierarchy
    */
   public final int THH;

   @Override
   final public Pool<? super T> superType() {
      return superPool;
   }

   /**
    * the next pool; used for efficient type hierarchy traversal.
    * 
    * @note on setting nextPool, the bpo of nextPool will be adjusted iff it is
    *       0 to allow insertion of types from the
    *       tool specification
    */
   Pool<?> next;

   /**
    * pointer to base-pool-managed data array
    */
   protected Obj[] data;

   /**
    * names of known fields, the actual field information is given in the
    * generated addKnownFiled method.
    */
   protected String KFN(int id) {
      return null;
   }

   /**
    * construct the known field with the given id
    */
   protected FieldDeclaration<?, T> KFC(int id, FieldType<?>[] SIFA,
         int nextFID) {
      return null;
   }

   /**
    * all fields that are declared as auto, including ObjectID
    * 
    * @note stores fields at index "-f.index"
    * @note sub-constructor adds auto fields from super types to this array;
    *       this is an optimization to make iteration
    *       O(1); the array cannot change anyway
    * @note the initial type constructor will already allocate an array of the
    *       correct size, because the right size is
    *       statically known (a generation time constant)
    */
   protected final AutoField<?, T>[] autoFields;
   /**
    * used as placeholder, if there are no auto fields at all to optimize
    * allocation time and memory usage
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
    * All stored objects, which have exactly the type T. Objects are stored as
    * arrays of field entries. The types of
    * the respective fields can be retrieved using the fieldTypes map.
    */
   protected final ArrayList<T> newObjects;

   /**
    * Ensures that at least capacity many new objects can be stored in this pool
    * without moving references.
    */
   public final void hintNewObjectsSize(int capacity) {
      newObjects.ensureCapacity(capacity);
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
    * @note in contrast to SKilL/Java, we maintain this as an internal invariant
    *       only!
    */
   int cachedSize;

   /**
    * number of deleted objects in this state
    */
   int deletedCount = 0;

   @Override
   final public String name() {
      return name;
   }

   /**
    * @note the unchecked cast is required, because we can not supply this as an
    *       argument in a super constructor, thus
    *       the base pool can not be an argument to the constructor. The cast
    *       will never fail anyway.
    */
   @SuppressWarnings("unchecked")
   protected Pool(int poolIndex, String name, Pool<? super T> superPool,
         int afSize) {
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

      dataFields = new ArrayList<>();
      this.autoFields = 0 == afSize ? (AutoField[]) noAutoFields
            : new AutoField[afSize];

      this.newObjects = new ArrayList<>();
   }

   protected abstract void allocateInstances();

   /**
    * Return an object by ID. Can only be used for objects with positive IDs.
    *
    * @note do not use this method if your understanding of Object IDs is not
    *       solid.
    * @note We do not allow getting objects with negative IDs because negative
    *       IDs are monomorphic. The code required
    *       to make them polymorphic is a straight forward access to owner.pool
    *       and to make a get there, but since get
    *       is used a lot, we do not want to increase its size for as little
    *       benefit as it would be to the user. Also,
    *       that solution would require a second argument of either type class
    *       or string.
    * @throws nothrow
    * @return the instance matching argument object id or null
    */
   @Override
   @SuppressWarnings("unchecked")
   final public T get(int ID) {
      int index = ID - 1;
      if (null == data || (index < 0 | data.length <= index))
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
    * Add an existing instance as a new object
    * 
    * @note Do not use objects managed by other OGFiles.
    */
   public final boolean add(T e) {
      if (e.ID != 0)
         throw new OGSSException(
               "the argument element already belongs to a state; you can transfer it by deleting in there first");

      e.ID = -1 - newObjects.size();
      return newObjects.add(e);
   }

   /**
    * Delete shall only be called from OGSS state
    * 
    * @param target
    *               the object to be deleted
    * @note we type target using the erasure directly, because the Java type
    *       system is too weak to express correct
    *       typing, when taking the pool from a map
    */
   final void delete(final Obj target) {
      final int ID = target.ID;
      if (0 != ID) {
         // check that target is in fact managed by this state
         if ((0 < ID & null != data && ID <= data.length
               && target == data[ID - 1])
               || (ID < 0 & -ID <= newObjects.size()
                     && target == newObjects.get(-1 - ID))) {
            target.ID = 0;
            deletedCount++;
         } else {
            throw new OGSSException(
                  "cannot delete an object that is not managed by this pool");
         }
      }
   }

   @Override
   final public DynamicDataIterator<T> iterator() {
      return new DynamicDataIterator<>(this);
   }

   /**
    * Get the name of known sub pool with argument local id. Return null, if id
    * is invalid.
    */
   protected String nameSub(int id) {
      return null;
   }

   /**
    * Create the known sub pool with argument local id. Return null, if id is
    * invalid.
    */
   protected Pool<? extends T> makeSub(int id, int index) {
      return null;
   }

   /**
    * Create an unknown sub pool with the argument name
    */
   @SuppressWarnings("unchecked")
   protected Pool<? extends T> makeSub(int index, String name) {
      return (Pool<? extends T>) new SubPool<UnknownObject>(index, name,
            UnknownObject.class, (Pool<? super UnknownObject>) this);
   }
}
