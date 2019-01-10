package ogss.common.java.api;

/**
 * Top level implementation of all SKilL related exceptions.
 * 
 * @author Timm Felden
 */
public class SkillException extends RuntimeException {

    public SkillException() {
    }

    public SkillException(String message) {
        super(message);
    }

    public SkillException(Throwable cause) {
        super(cause);
    }

    public SkillException(String message, Throwable cause) {
        super(message, cause);
    }

    public SkillException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
