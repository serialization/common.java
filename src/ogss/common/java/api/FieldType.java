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
     * the ID of this type (respective to the state it lives in)
     */
    public final int typeID;

    /**
     * @return the human readable type name
     */
    @Override
    public abstract String toString();

    protected FieldType(int typeID) {
        this.typeID = typeID;
    }
}
