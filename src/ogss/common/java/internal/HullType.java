package ogss.common.java.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;

import ogss.common.streams.BufferedOutStream;
import ogss.common.streams.InStream;
import ogss.common.streams.MappedInStream;
import ogss.common.streams.OutStream;

/**
 * This type subsumes all types whose serialization uses a hull-field.
 * 
 * @author Timm Felden
 */
public abstract class HullType<T> extends ByRefType<T> {

    /**
     * The field ID used by this hull on write.
     */
    int fieldID;

    /**
     * The number of other fields currently depending on this type. It is set by Writer on serialization in Tco.
     * 
     * @note If another field reduces deps to 0 it has to start a write job for this type.
     * @note This is in essence reference counting on an acyclic graph while writing data to disk.
     */
    int deps = 0;

    /**
     * The maximal, i.e. static, number of serialized fields depending on this type.
     * 
     * @note Can be 0.
     * @note If 0, the HullType is excluded from serialization.
     */
    int maxDeps = 0;

    /**
     * The current number of pending blocks. 0 if the HD is not split into blocks. This number is only meaningful while
     * writing a file.
     */
    int blocks;

    /**
     * The maximum size of a block.
     */
    protected static final int HD_Threshold = 16384;

    /**
     * get object by ID
     */
    protected final ArrayList<T> idMap;

    protected final IdentityHashMap<T, Integer> IDs = new IdentityHashMap<>();

    final void resetSerialization() {
        IDs.clear();

        // throw away id map, as it is no longer valid
        idMap.clear();
        idMap.add(null);
    }

    /**
     * Read the hull data from the stream. Abstract, because the inner loop is type-dependent anyway.
     * 
     * @note the fieldID is written by the caller
     * @return true iff hull shall be discarded (i.e. it is empty)
     */
    protected abstract void read(int block, MappedInStream map) throws IOException;

    /**
     * Write the hull into the stream. Abstract, because the inner loop is type-dependent anyway.
     * 
     * @note the fieldID is written by the caller
     * @return true iff hull shall be discarded (i.e. it is empty)
     */
    protected abstract boolean write(int block, BufferedOutStream out) throws IOException;

    protected HullType(int typeID) {
        super(typeID);
        idMap = new ArrayList<>();
        idMap.add(null);
    }

    /**
     * Return the id of the argument ref. This method is thread-safe. The id returned by this function does not change
     * per invocation.
     */
    int id(T ref) {
        if (null == ref)
            return 0;
        synchronized (this) {
            Integer rval = IDs.get(ref);
            if (null == rval) {
                final int ID = idMap.size();
                idMap.add(ref);
                IDs.put(ref, ID);
                return ID;
            }
            return rval;
        }
    }

    @Override
    public final T r(InStream in) {
        return get(in.v32());
    }

    @Override
    public final boolean w(T v, OutStream out) throws IOException {
        if (null == v) {
            out.i8((byte) 0);
            return true;
        }

        out.v64(id(v));
        return false;
    }

    /**
     * Allocate instances when reading a file.
     * 
     * @note map is positioned after count, i.e. the bucketID is still in the stream
     * @return the bucketID belonging to this allocation
     */
    protected abstract int allocateInstances(int count, MappedInStream map);

    @Override
    public final State owner() {
        throw new Error("TODO");
    }
}
