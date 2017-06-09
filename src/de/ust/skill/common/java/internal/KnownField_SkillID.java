package de.ust.skill.common.java.internal;

import de.ust.skill.common.java.internal.fieldDeclarations.AutoField;
import de.ust.skill.common.java.internal.fieldTypes.V64;

/**
 * SKilL IDs behave as if they were auto fields of type V64
 * 
 * @author Timm Felden
 */
public final class KnownField_SkillID<T extends SkillObject> extends AutoField<Long, T> {

    public KnownField_SkillID(StoragePool<T, ? super T> storagePool) {
        super(V64.get(), "skillid", 0, storagePool);
    }

    @Override
    public Long get(SkillObject ref) {
        return ref.skillID;
    }

    @Override
    public void set(SkillObject ref, Long value) {
        ref.skillID = value;
    }

    @Override
    void check() {
        // always correct
    }
}
