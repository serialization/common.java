package de.ust.skill.common.java.api;

import java.util.Collection;
import java.util.Iterator;

/**
 * Access to arbitrary skill type <T> including interfaces.
 * 
 * @author Timm Felden
 *
 * @note This type is only required because Javas type system is rather weak.
 */
public interface GeneralAccess<T> extends Collection<T> {

    /**
     * @return the skill name of the type
     */
    String name();

    /**
     * @return a type ordered Container iterator over all instances of T
     * @note do not invoke this function, if you do not know what "type order"
     *       means
     */
    Iterator<T> typeOrderIterator();

}