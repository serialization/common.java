package de.ust.skill.common.java.api;

/**
 * Access to arbitrary skill type <T> including interfaces.
 * 
 * @author Timm Felden
 *
 * @note This type is only required because Javas type system is rather weak.
 */
public interface GeneralAccess<T> extends Iterable<T> {

    /**
     * @return the skill name of the type
     */
    String name();

    /**
     * @return the number of objects returned by the default iterator
     */
    public int size();
}