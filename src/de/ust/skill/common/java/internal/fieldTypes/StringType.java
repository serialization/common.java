package de.ust.skill.common.java.internal.fieldTypes;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;

import de.ust.skill.common.java.internal.FieldType;
import de.ust.skill.common.java.internal.StringPool;
import de.ust.skill.common.jvm.streams.InStream;
import de.ust.skill.common.jvm.streams.OutStream;

/**
 * String types are instantiated once per state.
 * 
 * @author Timm Felden
 */
public final class StringType extends FieldType<String> implements ReferenceType {

    private final StringPool strings;
    protected HashMap<String, Integer> stringIDs = null;

    public StringType(StringPool strings) {
        super(14);
        this.strings = strings;
        assert strings != null;
    }

    @Override
    public String readSingleField(InStream in) {
        return strings.get(in.v32());
    }

    @Override
    public long calculateOffset(Collection<String> xs) {
        // shortcut for small string pools
        if (stringIDs.size() < 128)
            return xs.size();

        long result = 0L;
        for (String s : xs) {
            result += null == s ? 1 : V64.singleV64Offset(stringIDs.get(s));
        }

        return result;
    }

    public long singleOffset(String name) {
        return null == name ? 1 : V64.singleV64Offset(stringIDs.get(name));
    }

    @Override
    public void writeSingleField(String v, OutStream out) throws IOException {
        if (null == v)
            out.i8((byte) 0);
        else
            out.v64(stringIDs.get(v));

    }

    /**
     * internal use only!
     * 
     * @note invoked at begin of serialization
     */
    public HashMap<String, Integer> resetIDs() {
        stringIDs = new HashMap<>();
        return stringIDs;
    }

    /**
     * internal use only!
     * 
     * @note invoked at end of serialization
     */
    public void clearIDs() {
        stringIDs = null;
    }

    @Override
    public String toString() {
        return "string";
    }
}
