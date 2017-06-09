package de.ust.skill.common.java.internal;

import java.util.Set;

import de.ust.skill.common.java.internal.fieldDeclarations.AutoField;

/**
 * Management of sub types differs from base types.
 * 
 * @author Timm Felden
 */
@Deprecated
public class SubPool<T extends B, B extends SkillObject> extends StoragePool<T, B> {

    public SubPool(int poolIndex, String name, StoragePool<? super T, B> superPool, Set<String> knownFields,
            AutoField<?, T>[] autoFields) {
        super(poolIndex, name, superPool, knownFields, autoFields);
    }
}
