package ogss.common.java.internal;

import ogss.common.java.api.GeneralAccess;

/**
 * This type subsumes all types whose instances have by-ref semantics. Those are Strings, Containers and Objects.
 * All by-ref types assign IDs to their instances and are, hence, iterable. 
 * 
 * @note ByRefTypes have per-state unique names
 * @author Timm Felden
 */
public abstract class ByRefType<T> extends FieldType<T> implements GeneralAccess<T> {

    protected ByRefType(int typeID) {
        super(typeID);
    }

}
