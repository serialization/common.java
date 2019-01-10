package ogss.common.java.internal.fieldTypes;

import ogss.common.java.internal.FieldType;

/**
 * Mutable floats.
 * 
 * @author Timm Felden
 */
public abstract class FloatType<T> extends FieldType<T> {
    protected FloatType(int typeID) {
        super(typeID);
    }
}
