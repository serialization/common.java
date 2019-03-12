package ogss.common.java.internal;

import ogss.common.streams.MappedInStream;

/**
 * The field is distributed and loaded on demand. Unknown fields are lazy as well.
 *
 * @author Timm Felden
 * @note implementation abuses a distributed field that can be accessed iff there are no data chunks to be processed
 * @note offset and write methods will not be overwritten, because forcing has to happen even before resetChunks
 */
public final class LazyField<T, Ref extends Obj> extends DistributedField<T, Ref> {

    public LazyField(FieldType<T> type, String name, int id, Pool<Ref> owner) {
        super(type, name, id, owner);
    }

    // deferred reading info: valid after read
    private int first;
    private int last;
    // is loaded <-> chunkMap == null
    private MappedInStream buffer;

    // executes pending read operations
    private void load() {
        super.read(first, last, buffer);
        buffer = null;
    }

    // required to ensure that data is present before state reorganization
    void ensureLoaded() {
        if (null != buffer)
            load();
    }

    @Override
    protected final void read(int i, int h, MappedInStream in) {
        first = i;
        last = h;
        buffer = in;
    }

    @Override
    public T get(Obj ref) {
        if (ref.ID < 0)
            return newData.get(ref);

        if (null != buffer)
            load();

        return super.get(ref);
    }

    @Override
    public void set(Obj ref, T value) {
        if (-1 == ref.ID)
            newData.put(ref, value);
        else {
            if (null != buffer)
                load();

            super.set(ref, value);
        }
    }
}
