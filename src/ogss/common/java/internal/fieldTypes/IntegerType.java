package ogss.common.java.internal.fieldTypes;

import ogss.common.java.internal.FieldType;

/**
 * Mutable integers.
 * 
 * @author Timm Felden
 */
public abstract class IntegerType<T> extends FieldType<T> {
    protected IntegerType(int typeID) {
        super(typeID);
    }
}
