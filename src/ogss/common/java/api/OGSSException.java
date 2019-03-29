package ogss.common.java.api;

/**
 * Super type for all OGSS-related errors.
 * 
 * @author Timm Felden
 */
public class OGSSException extends RuntimeException {

    public OGSSException() {
    }

    public OGSSException(String message) {
        super(message);
    }

    public OGSSException(Throwable cause) {
        super(cause);
    }

    public OGSSException(String message, Throwable cause) {
        super(message, cause);
    }

    public OGSSException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
