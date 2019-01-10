package ogss.common.java.internal.fieldTypes;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;

import ogss.common.java.internal.FieldType;
import ogss.common.streams.InStream;
import ogss.common.streams.OutStream;

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
    public HashMap<K, V> r(InStream in) {
        // TODO incorrect
        int i = in.v32();
        HashMap<K, V> rval = new HashMap<>(1 + (i * 3) / 2);
        while (i-- != 0)
            rval.put(keyType.r(in), valueType.r(in));
        return rval;
    }


    @SuppressWarnings("null")
    @Override
    public void w(HashMap<K, V> data, OutStream out) throws IOException {
        // TODO incorrect
        int size = (null == data) ? 0 : data.size();
        if (0 == size) {
            out.i8((byte) 0);
            return;
        }
        out.v64(size);
        for (Entry<K, V> e : data.entrySet()) {
            keyType.w(e.getKey(), out);
            valueType.w(e.getValue(), out);
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
