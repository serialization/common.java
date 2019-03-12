package ogss.common.java.internal;

import java.io.IOException;
import java.util.IdentityHashMap;

import ogss.common.java.internal.fieldTypes.BoolType;
import ogss.common.streams.BoolOutWrapper;
import ogss.common.streams.BufferedOutStream;
import ogss.common.streams.MappedInStream;

/**
 * The fields data is distributed into an array (for now its a hash map) holding its instances.
 */
public class DistributedField<T, Ref extends Obj> extends FieldDeclaration<T, Ref> {

    public DistributedField(FieldType<T> type, String name, int id, Pool<Ref> owner) {
        super(type, name, id, owner);
    }

    // data held as in storage pools
    // @note C++-style implementation is not possible on JVM
    protected final IdentityHashMap<Obj, T> data = new IdentityHashMap<>();
    protected final IdentityHashMap<Obj, T> newData = new IdentityHashMap<>();

    @Override
    protected void read(int i, final int h, MappedInStream in) {
        final Obj[] d = owner.basePool.data;
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
        final Obj[] d = owner.data;
        if (type instanceof BoolType) {
            BoolOutWrapper wrap = new BoolOutWrapper(out);
            for (; i < h; i++) {
                boolean v = Boolean.TRUE == data.get(d[i]);
                wrap.bool(v);
                drop &= !v;
            }
            wrap.unwrap();
        } else {
            for (; i < h; i++) {
                drop &= type.w(data.get(d[i]), out);
            }
        }
        return drop;
    }

    @Override
    public T get(Obj ref) {
        if (ref.ID < 0)
            return newData.get(ref);

        T r = data.get(ref);
        // fix returned values for dropped distributed fields
        if (null == r) {
            switch (type.typeID) {
            case 0:
                return (T) Boolean.FALSE;
            case 1:
                return (T) (Byte) (byte) 0;
            case 2:
                return (T) (Short) (short) 0;
            case 3:
                return (T) (Integer) 0;
            case 4:
            case 5:
                return (T) (Long) 0L;
            case 6:
                return (T) (Float) 0f;
            case 7:
                return (T) (Double) 0.0;
            }
            if (type instanceof EnumPool<?>)
                return (T) ((EnumPool<?>) type).fileValues[0];
        }
        return r;
    }

    @Override
    public void set(Obj ref, T value) {
        if (-1 == ref.ID)
            newData.put(ref, value);
        else
            data.put(ref, value);

    }

}
