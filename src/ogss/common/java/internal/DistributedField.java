package ogss.common.java.internal;

import java.io.IOException;
import java.util.HashMap;

import ogss.common.streams.BufferedOutStream;
import ogss.common.streams.MappedInStream;

/**
 * The fields data is distributed into an array (for now its a hash map) holding its instances.
 */
public class DistributedField<T, Obj extends Pointer> extends FieldDeclaration<T, Obj> {

    public DistributedField(FieldType<T> type, String name, int id, Pool<Obj, ? super Obj> owner) {
        super(type, name, id, owner);
    }

    // data held as in storage pools
    // @note C++-style implementation is not possible on JVM
    protected final HashMap<Pointer, T> data = new HashMap<>();
    protected final HashMap<Pointer, T> newData = new HashMap<>();

    @Override
    protected void read(int i, final int h, MappedInStream in) {
        final Pointer[] d = owner.basePool.data;
        for (; i != h; i++) {
            data.put(d[i], type.r(in));
        }
    }

    /**
     * compress this field
     * 
     * @note for now, deleted elements can survive in data
     */
    void compress() {
        data.putAll(newData);
        newData.clear();
    }

    @Override
    protected final boolean write(int i, final int h, BufferedOutStream out) throws IOException {
        boolean drop = true;
        final Pointer[] d = owner.basePool.data;
        for (; i < h; i++) {
            drop &= type.w(data.get(d[i]), out);
        }
        return drop;
    }

    @Override
    public T get(Pointer ref) {
        if (-1 == ref.ID)
            return newData.get(ref);

        return data.get(ref);
    }

    @Override
    public void set(Pointer ref, T value) {
        if (-1 == ref.ID)
            newData.put(ref, value);
        else
            data.put(ref, value);

    }

}
