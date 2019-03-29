package ogss.common.java.internal.exceptions;

import ogss.common.java.api.FieldType;
import ogss.common.java.api.OGSSException;

/**
 * Thrown in case of a type miss-match on a field type.
 * 
 * @author Timm Felden
 */
public class TypeMissmatchError extends OGSSException {

    public TypeMissmatchError(FieldType<?> type, String expected, String field, String pool) {
        super(String.format("During construction of %s.%s: Encountered incompatible type \"%s\" (expected: %s)", pool,
                field, type.toString(), expected));
    }

}
