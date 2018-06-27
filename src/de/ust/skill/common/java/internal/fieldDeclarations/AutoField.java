package de.ust.skill.common.java.internal.fieldDeclarations;

import java.io.IOException;

import de.ust.skill.common.java.internal.FieldDeclaration;
import de.ust.skill.common.java.internal.FieldType;
import de.ust.skill.common.java.internal.SkillObject;
import de.ust.skill.common.java.internal.StoragePool;
import de.ust.skill.common.java.internal.parts.BulkChunk;
import de.ust.skill.common.jvm.streams.MappedInStream;
import de.ust.skill.common.jvm.streams.MappedOutStream;

/**
 * This trait marks auto fields.
 * 
 * @author Timm Felden
 */
public abstract class AutoField<T, Obj extends SkillObject> extends FieldDeclaration<T, Obj>
        implements KnownField<T, Obj> {
    protected AutoField(FieldType<T> type, String name, int index, StoragePool<Obj, ? super Obj> owner) {
        super(type, name, index, owner);
    }

    @Override
    protected void rsc(int i, final int h, MappedInStream in) {
        throw new NoSuchMethodError("one can not read auto fields!");
    }

    @Override
    protected final void rbc(BulkChunk last, MappedInStream in) {
        throw new NoSuchMethodError("one can not read auto fields!");
    }

    @Override
    protected void osc(int i, int end) {
        throw new NoSuchMethodError("one get the offset of an auto fields!");
    }

    @Override
    protected void wsc(int i, int end, MappedOutStream out) throws IOException {
        throw new NoSuchMethodError("one can not write auto fields!");
    }
}
