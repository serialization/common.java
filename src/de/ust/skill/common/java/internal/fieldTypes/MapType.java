package de.ust.skill.common.java.internal.fieldTypes;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map.Entry;

import de.ust.skill.common.java.internal.FieldType;
import de.ust.skill.common.jvm.streams.InStream;
import de.ust.skill.common.jvm.streams.OutStream;

public final class MapType<K, V> extends CompoundType<HashMap<K, V>> {

    /**
     * @see SKilL V1.0 reference manual §G
     */
    public static final int typeID = 20;

    public final FieldType<K> keyType;
    public final FieldType<V> valueType;

    public MapType(FieldType<K> keyType, FieldType<V> valueType) {
        super(typeID);
        this.keyType = keyType;
        this.valueType = valueType;
    }

    @Override
    public HashMap<K, V> readSingleField(InStream in) {
        int i = in.v32();
        HashMap<K, V> rval = new HashMap<>(1 + (i * 3) / 2);
        while (i-- != 0)
            rval.put(keyType.readSingleField(in), valueType.readSingleField(in));
        return rval;
    }

    @Override
    public long calculateOffset(Collection<HashMap<K, V>> xs) {
        long result = 0L;
        for (HashMap<K, V> x : xs) {
            int size = x.size();
            if (0 == size)
                result++;
            else {
                result += V64.singleV64Offset(size) + keyType.calculateOffset(x.keySet())
                        + valueType.calculateOffset(x.values());
            }
        }

        return result;
    }

    @SuppressWarnings("null")
    @Override
    public long singleOffset(HashMap<K, V> x) {
        int size = null == x ? 0 : x.size();
        if (0 == size)
            return 1L;

        return V64.singleV64Offset(x.size()) + keyType.calculateOffset(x.keySet())
                + valueType.calculateOffset(x.values());
    }

    @SuppressWarnings("null")
    @Override
    public void writeSingleField(HashMap<K, V> data, OutStream out) throws IOException {
        int size = (null == data) ? 0 : data.size();
        if (0 == size) {
            out.i8((byte) 0);
            return;
        }
        out.v64(size);
        for (Entry<K, V> e : data.entrySet()) {
            keyType.writeSingleField(e.getKey(), out);
            valueType.writeSingleField(e.getValue(), out);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("map<");
        sb.append(keyType).append(", ").append(valueType).append(">");
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MapType<?, ?>)
            return keyType.equals(((MapType<?, ?>) obj).keyType) && valueType.equals(((MapType<?, ?>) obj).valueType);
        return false;
    }
}
