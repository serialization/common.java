package ogss.common.java.internal.fieldTypes;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import ogss.common.java.internal.FieldType;
import ogss.common.java.internal.HullType;
import ogss.common.streams.BufferedOutStream;
import ogss.common.streams.MappedInStream;

public final class MapType<K, V> extends HullType<HashMap<K, V>> {

    public final FieldType<K> keyType;
    public final FieldType<V> valueType;

    public MapType(int typeID, FieldType<K> keyType, FieldType<V> valueType) {
        super(typeID);

        this.keyType = keyType;
        this.valueType = valueType;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MapType<?, ?>)
            return keyType.equals(((MapType<?, ?>) obj).keyType) && valueType.equals(((MapType<?, ?>) obj).valueType);
        return false;
    }

    @Override
    public String name() {
        StringBuilder sb = new StringBuilder("map<");
        sb.append(keyType).append(",").append(valueType).append(">");
        return sb.toString();
    }

    @Override
    public int size() {
        return IDs.size();
    }

    @Override
    public HashMap<K, V> get(int ID) {
        return idMap.get(ID);
    }

    @Override
    public Iterator<HashMap<K, V>> iterator() {
        return IDs.keySet().iterator();
    }

    protected MappedInStream in;

    @Override
    protected void read() throws IOException {
        final int count = idMap.size() - 1;
        for (int i = 1; i <= count; i++) {
            HashMap<K, V> xs = idMap.get(i);
            int s = in.v32();
            while (s-- != 0) {
                final K k = keyType.r(in);
                final V v = valueType.r(in);
                xs.put(k, v);
            }
        }
    }

    @Override
    protected boolean write(BufferedOutStream out) throws IOException {
        final int count = idMap.size() - 1;
        if (0 != count) {
            out.v64(count);
            for (int i = 1; i <= count; i++) {
                HashMap<K, V> xs = idMap.get(i);
                out.v64(xs.size());
                for (Entry<K, V> e : xs.entrySet()) {
                    keyType.w(e.getKey(), out);
                    valueType.w(e.getValue(), out);
                }
            }
            return false;
        }
        return true;
    }

    @Override
    protected void allocateInstances(int count, MappedInStream in) {
        this.in = in;
        while (count-- != 0)
            idMap.add(new HashMap<>());
    }
}
