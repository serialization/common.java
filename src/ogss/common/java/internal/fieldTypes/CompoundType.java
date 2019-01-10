package ogss.common.java.internal.fieldTypes;

import ogss.common.java.internal.FieldType;

/**
 * Super class of all container types.
 * 
 * @author Timm Felden
 */
public abstract class CompoundType<T> extends FieldType<T> {
    protected CompoundType(int typeID) {
        super(typeID);
    }
}
