package ogss.common.java.internal.fieldTypes;

import java.util.Collection;
import java.util.Iterator;

import ogss.common.java.internal.FieldType;
import ogss.common.java.internal.HullType;

/**
 * Super class of all container types with one type argument.
 * 
 * @author Timm Felden
 */
public abstract class SingleArgumentType<T extends Collection<Base>, Base> extends HullType<T> {
    public final FieldType<Base> base;

    public SingleArgumentType(int typeID, FieldType<Base> base) {
        super(typeID);
        this.base = base;
    }

    @Override
    public int size() {
        return IDs.size();
    }

    @Override
    public Iterator<T> iterator() {
        return IDs.keySet().iterator();
    }

    @Override
    public final T get(int ID) {
        return idMap.get(ID);
    }
}
