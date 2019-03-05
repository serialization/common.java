package ogss.common.java.internal;

import ogss.common.java.internal.fieldDeclarations.KnownField;

public abstract class KnownDataField<T, Ref extends Obj> extends FieldDeclaration<T, Ref>
        implements KnownField<T, Obj> {

    protected KnownDataField(FieldType<T> type, String name, int id, Pool<Ref, ?> owner) {
        super(type, name, id, owner);
    }
}
