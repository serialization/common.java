package de.ust.skill.common.java.api;

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
     * @return a human readable and unique representation of this type
     */
    @Override
    public abstract String toString();
}
