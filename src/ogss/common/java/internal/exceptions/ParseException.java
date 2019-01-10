package ogss.common.java.internal.exceptions;

import ogss.common.java.api.SkillException;
import ogss.common.streams.InStream;

/**
 * This exception is used if byte stream related errors occur.
 *
 * @author Timm Felden
 */
public final class ParseException extends SkillException {
    public ParseException(SkillException cause, String msg) {
        super(msg, cause);
    }
    
    public ParseException(InStream in, Throwable cause, String msg) {
        super(String.format("At 0x%x: %s", in.position(), msg), cause);
    }

    public ParseException(InStream in, Throwable cause, String msgFormat, Object... msgArgs) {
        super(String.format("At 0x%x: %s", in.position(), String.format(msgFormat, msgArgs)),
                cause);
    }
}
