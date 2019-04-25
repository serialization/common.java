package ogss.common.java.internal;

import java.io.IOException;

import ogss.common.streams.InStream;
import ogss.common.streams.OutStream;

/**
 * Top level implementation of a field type, the runtime representation of a fields type.
 * 
 * @param <T>
 *            the Java type to represent instances of this field type
 * @note representation of the type system relies on invariants and heavy abuse of type erasure TODO overriding hashCode
 *       with typeID was likely never correct. What is the purpose? Try to get rid of it!
 * @author Timm Felden
 */
abstract public class FieldType<T> extends ogss.common.java.api.FieldType<T> {

    protected FieldType(int typeID) {
        super(typeID);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof FieldType<?>)
            return ((FieldType<?>) obj).typeID == typeID;
        return false;
    }

    @Override
    public final int hashCode() {
        return typeID;
    }

    /**
     * Read one T out of the stream.
     *
     * @note this function has to be implemented by FieldTypes because of limits of the Java type system (and any other
     *       sane type system)
     * @note intended for internal usage only!
     */
    public abstract T r(InStream in);

    /**
     * Write one T into the stream.
     *
     * @note this function has to be implemented by FieldTypes because of limits of the Java type system (and any other
     *       sane type system)
     * @note intended for internal usage only!
     * @return true iff a default value was written
     */
    public abstract boolean w(T data, OutStream out) throws IOException;
}
