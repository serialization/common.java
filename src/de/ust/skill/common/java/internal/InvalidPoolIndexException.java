package de.ust.skill.common.java.internal;

import de.ust.skill.common.java.api.SkillException;

/**
 * Thrown, if an index into a pool is invalid.
 *
 * @author Timm Felden
 */
public class InvalidPoolIndexException extends SkillException {

    public InvalidPoolIndexException(long index, int size, String pool) {
        super(String.format("Invalid index %d into pool %s of size %d", index, pool, size));
    }

    public InvalidPoolIndexException(long index, int size, String pool, Exception cause) {
        super(String.format("Invalid index %d into pool %s of size %d", index, pool, size), cause);
    }
}
