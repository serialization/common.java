package ogss.common.java.internal;

import ogss.common.java.internal.fieldDeclarations.KnownField;

public abstract class KnownDataField<T, Obj extends Pointer> extends FieldDeclaration<T, Obj>
        implements KnownField<T, Obj> {

    protected KnownDataField(FieldType<T> type, String name, Pool<Obj, ? super Obj> owner) {
        super(type, name, owner);
    }
}
