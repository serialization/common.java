package de.ust.skill.common.java.internal.fieldTypes;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map.Entry;

import de.ust.skill.common.java.internal.FieldType;
import de.ust.skill.common.jvm.streams.InStream;
import de.ust.skill.common.jvm.streams.OutStream;

public final class MapType<K, V> extends CompoundType<HashMap<K, V>> {
    public final FieldType<K> keyType;
    public final FieldType<V> valueType;

    public MapType(FieldType<K> keyType, FieldType<V> valueType) {
        super(20);
        this.keyType = keyType;
        this.valueType = valueType;
    }

    @Override
    public HashMap<K, V> readSingleField(InStream in) {
        HashMap<K, V> rval = new HashMap<>();
        for (int i = (int) in.v64(); i != 0; i--)
            rval.put(keyType.readSingleField(in), valueType.readSingleField(in));
        return rval;
    }

    @Override
    public long calculateOffset(Collection<HashMap<K, V>> xs) {
        long result = 0L;
        for (HashMap<K, V> x : xs) {
            result += V64.singleV64Offset(x.size());
            result += keyType.calculateOffset(x.keySet());
            result += valueType.calculateOffset(x.values());
        }

        return result;
    }

    @Override
    public void writeSingleField(HashMap<K, V> data, OutStream out) throws IOException {
        if (null == data || data.isEmpty()) {
            out.i8((byte) 0);
            return;
        }
        out.v64(data.size());
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
