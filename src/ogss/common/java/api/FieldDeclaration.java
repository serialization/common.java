package ogss.common.java.api;

import ogss.common.java.internal.Obj;

/**
 * An abstract Field declaration, used for the runtime representation of types.
 * It can be used for reflective access of types.
 * 
 * @author Timm Felden
 * @param <T>
 *            runtime type of the field modulo boxing
 */
public abstract class FieldDeclaration<T> {
    /**
     * @return the skill type of this field
     */
    public abstract FieldType<T> type();

    /**
     * @return skill name of this field
     */
    public abstract String name();

    /**
     * @return enclosing type
     */
    public abstract GeneralAccess<?> owner();

    /**
     * Generic getter for an object.
     * 
     * @note it is up to the user to ensure that the field is valid for ref.
     */
    public abstract T get(Obj ref);

    /**
     * Generic setter for an object.
     * 
     * @note it is up to the user to ensure that the field is valid for ref.
     */
    public abstract void set(Obj ref, T value);
}
