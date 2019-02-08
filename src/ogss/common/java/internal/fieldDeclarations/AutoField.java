package ogss.common.java.internal.fieldDeclarations;

import java.io.IOException;

import ogss.common.java.internal.FieldDeclaration;
import ogss.common.java.internal.FieldType;
import ogss.common.java.internal.Pointer;
import ogss.common.java.internal.Pool;
import ogss.common.streams.BufferedOutStream;
import ogss.common.streams.MappedInStream;

/**
 * This trait marks auto fields.
 * 
 * @author Timm Felden
 */
public abstract class AutoField<T, Obj extends Pointer> extends FieldDeclaration<T, Obj>
        implements KnownField<T, Obj> {
    protected AutoField(FieldType<T> type, String name, int index, Pool<Obj, ? super Obj> owner) {
        super(type, name, index, owner);
    }

    @Override
    protected void read(int i, final int h, MappedInStream in) {
        throw new NoSuchMethodError("one cannot read auto fields!");
    }

    @Override
    protected boolean write(int i, int end, BufferedOutStream out) throws IOException {
        throw new NoSuchMethodError("one cannot write auto fields!");
    }
}
