package ogss.common.java.internal;

import ogss.common.streams.BufferedOutStream;
import ogss.common.streams.MappedInStream;

import java.io.IOException;
import java.util.Iterator;

public abstract class ContainerType<T> extends HullType<T> {
    public ContainerType(int typeID) {
        super(typeID);
    }

    /**
     * The current number of pending blocks. 0 if the HD is not split into blocks. This number is only meaningful while
     * writing a file.
     */
    int blocks;

    /**
     * Read the hull data from the stream. Abstract, because the inner loop is type-dependent anyway.
     *
     * @return true iff hull shall be discarded (i.e. it is empty)
     * @note the fieldID is written by the caller
     */
    protected abstract void read(int i, final int end, MappedInStream in) throws IOException;

    /**
     * Write the hull into the stream. Abstract, because the inner loop is type-dependent anyway.
     *
     * @return true iff hull shall be discarded (i.e. it is empty)
     * @note the fieldID is written by the caller
     */
    protected abstract void write(int i, final int end, BufferedOutStream out) throws IOException;

    @Override
    public final Iterator<T> iterator() {
        Iterator<T> r = idMap.iterator();
        r.next(); // skip null
        return r;
    }

    @Override
    public final T get(int ID) {
        return idMap.get(ID);
    }
}
