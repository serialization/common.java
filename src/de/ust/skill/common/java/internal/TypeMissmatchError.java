package de.ust.skill.common.java.internal;

import de.ust.skill.common.java.api.FieldType;
import de.ust.skill.common.java.api.SkillException;

/**
 * Thrown in case of a type miss-match on a field type.
 * 
 * @author Timm Felden
 */
public class TypeMissmatchError extends SkillException {

    public TypeMissmatchError(FieldType<?> type, String expected, String field, String pool) {
        super(String.format("During construction of %s.%s: Encountered incompatible type \"%s\" (expected: %s)", pool,
                field, type.toString(), expected));
    }

}
