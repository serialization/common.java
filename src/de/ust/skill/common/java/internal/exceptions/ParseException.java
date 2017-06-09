package de.ust.skill.common.java.internal.exceptions;

import de.ust.skill.common.java.api.SkillException;
import de.ust.skill.common.jvm.streams.InStream;

/**
 * This exception is used if byte stream related errors occur.
 *
 * @author Timm Felden
 */
public final class ParseException extends SkillException {
    public ParseException(SkillException cause, String msg) {
        super(msg, cause);
    }
    
    public ParseException(InStream in, int block, Throwable cause, String msg) {
        super(String.format("In block %d @0x%x: %s", block + 1, in.position(), msg), cause);
    }

    public ParseException(InStream in, int block, Throwable cause, String msgFormat, Object... msgArgs) {
        super(String.format("In block %d @0x%x: %s", block + 1, in.position(), String.format(msgFormat, msgArgs)),
                cause);
    }
}
