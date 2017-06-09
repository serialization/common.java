package de.ust.skill.common.java.internal.fieldDeclarations;

import de.ust.skill.common.java.api.SkillException;
import de.ust.skill.common.java.internal.FieldDeclaration;
import de.ust.skill.common.java.internal.FieldType;
import de.ust.skill.common.java.internal.SkillObject;
import de.ust.skill.common.java.internal.StoragePool;
import de.ust.skill.common.java.internal.parts.BulkChunk;
import de.ust.skill.common.java.internal.parts.SimpleChunk;
import de.ust.skill.common.jvm.streams.MappedInStream;
import de.ust.skill.common.jvm.streams.MappedOutStream;

/**
 * This trait marks auto fields.
 * 
 * @author Timm Felden
 */
public abstract class AutoField<T, Obj extends SkillObject> extends FieldDeclaration<T, Obj> {
    protected AutoField(FieldType<T> type, String name, int index, StoragePool<Obj, ? super Obj> owner) {
        super(type, name, index, owner);
    }

    @Override
    protected final void rsc(SimpleChunk last, MappedInStream in) {
        throw new NoSuchMethodError("one can not read auto fields!");
    }

    @Override
    protected final void rbc(BulkChunk last, MappedInStream in) {
        throw new NoSuchMethodError("one can not read auto fields!");
    }

    @Override
    public final long offset() {
        throw new NoSuchMethodError("one get the offset of an auto fields!");
    }

    @Override
    public final void write(MappedOutStream out) throws SkillException {
        throw new NoSuchMethodError("one can not write auto fields!");
    }
}
