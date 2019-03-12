package ogss.common.java.internal;

/**
 * An Obj that holds a pointer to its pool.
 * 
 * @author Timm Felden
 * @note This type definition is in internal, because we have to protect the user from tampering with ID
 */
public interface NamedObj {
    public abstract Pool<?> τp();
}
