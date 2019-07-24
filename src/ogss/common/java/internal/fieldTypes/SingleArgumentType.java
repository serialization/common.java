package ogss.common.java.internal.fieldTypes;

import java.util.Collection;

import ogss.common.java.internal.ContainerType;
import ogss.common.java.internal.FieldType;

/**
 * Super class of all container types with one type argument.
 * 
 * @author Timm Felden
 */
public abstract class SingleArgumentType<T extends Collection<Base>, Base> extends ContainerType<T> {
    public final FieldType<Base> base;

    public SingleArgumentType(int typeID, FieldType<Base> base) {
        super(typeID);
        this.base = base;
    }
}
