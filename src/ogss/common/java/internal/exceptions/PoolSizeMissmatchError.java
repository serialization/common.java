package ogss.common.java.internal.exceptions;

import java.nio.BufferUnderflowException;

import ogss.common.java.api.OGSSException;
import ogss.common.java.internal.FieldDeclaration;

/**
 * Thrown, if field deserialization consumes less bytes then specified by the header.
 *
 * @author Timm Felden
 */
public class PoolSizeMissmatchError extends OGSSException {

    public PoolSizeMissmatchError(long position, long begin, long end, FieldDeclaration<?, ?> field) {
        super(String.format("Corrupted data chunk at 0x%X between 0x%X and 0x%X in Field %s.%s of type: %s", position,
                begin, end, field.owner().name(), field.name(), field.type().toString()));
    }

    public PoolSizeMissmatchError(long begin, long end, FieldDeclaration<?, ?> field, BufferUnderflowException e) {
        super(String.format("Corrupted data chunk between 0x%X and 0x%X in Field %s.%s of type: %s", begin, end,
                field.owner().name(), field.name(), field.type().toString()), e);
    }

}
