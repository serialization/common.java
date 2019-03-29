package ogss.common.java.internal.exceptions;

import ogss.common.java.api.OGSSException;

/**
 * Thrown, if an index into a pool is invalid.
 *
 * @author Timm Felden
 */
public class InvalidPoolIndexException extends OGSSException {

    public InvalidPoolIndexException(long index, int size, String pool) {
        super(String.format("Invalid index %d into pool %s of size %d", index, pool, size));
    }

    public InvalidPoolIndexException(long index, int size, String pool, Exception cause) {
        super(String.format("Invalid index %d into pool %s of size %d", index, pool, size), cause);
    }
}
