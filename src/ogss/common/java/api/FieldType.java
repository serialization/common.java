package ogss.common.java.api;

/**
 * Field types as used in reflective access.
 * 
 * @author Timm Felden
 * @param <T>
 *            (boxed) runtime type of target objects
 */
public abstract class FieldType<T> {

    /**
     * @return the ID of this type (respective to the state in which it lives)
     */
    public abstract int typeID();

    /**
     * @return the human readable type name
     */
    @Override
    public abstract String toString();
}
