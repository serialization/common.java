package ogss.common.java.api;

import ogss.common.java.internal.State;

/**
 * Access to entities of an arbitrary by-ref OGSS type <T> including interfaces.
 * 
 * @author Timm Felden
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

    /**
     * @return the file owning this access
     */
    public State owner();

    /**
     * get an instance by its ID
     * 
     * @note This is only usable for instances with IDs and for valid IDs. This function is unrelated to Collection.get
     */
    public T get(int ID);
}