package ogss.common.java.internal.fieldTypes;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;

import ogss.common.java.internal.ContainerType;
import ogss.common.java.internal.FieldType;
import ogss.common.streams.BufferedOutStream;
import ogss.common.streams.MappedInStream;

public final class MapType<K, V> extends ContainerType<HashMap<K, V>> {

    public final FieldType<K> keyType;
    public final FieldType<V> valueType;

    public MapType(int typeID, FieldType<K> keyType, FieldType<V> valueType) {
        super(typeID);

        this.keyType = keyType;
        this.valueType = valueType;
    }

    @Override
    public String name() {
        StringBuilder sb = new StringBuilder("map<");
        sb.append(keyType).append(",").append(valueType).append(">");
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MapType<?, ?>)
            return keyType.equals(((MapType<?, ?>) obj).keyType) && valueType.equals(((MapType<?, ?>) obj).valueType);
        return false;
    }

    @Override
    protected int allocateInstances(int count, MappedInStream in) {
        // check for blocks
        if (count > HD_Threshold) {
            final int block = in.v32();
            // initialize idMap with null to allow parallel updates
            synchronized (this) {
                if (1 == idMap.size()) {
                    int c = count;
                    while (c-- != 0)
                        idMap.add(null);
                }
            }
            int i = block * HD_Threshold;
            final int end = Math.min(count, i + HD_Threshold);
            while (i < end)
                idMap.set(++i, new HashMap<>());

            return block;
        }
        // else, no blocks
        while (count-- != 0)
            idMap.add(new HashMap<>());
        return 0;
    }

    @Override
    protected final void read(int i, final int end, MappedInStream in) throws IOException {
        while (i < end) {
            HashMap<K, V> xs = idMap.get(++i);
            int s = in.v32();
            while (s-- != 0) {
                final K k = keyType.r(in);
                final V v = valueType.r(in);
                xs.put(k, v);
            }
        }
    }

    @Override
    protected final void write(int i, final int end, BufferedOutStream out) throws IOException {
        while (i < end) {
            HashMap<K, V> xs = idMap.get(++i);
            out.v64(xs.size());
            for (Entry<K, V> e : xs.entrySet()) {
                keyType.w(e.getKey(), out);
                valueType.w(e.getValue(), out);
            }
        }
    }
}
