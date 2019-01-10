package ogss.common.java.api;

import java.util.Collection;

/**
 * Provides access to Strings in the pool.
 * 
 * @note As it is the case with Strings in Java, Strings in SKilL are special
 *       objects that behave slightly different, because they are something in
 *       between numbers and objects.
 * @author Timm Felden
 */
public interface StringAccess extends Collection<String>, GeneralAccess<String> {
    /**
     * get String by its Skill ID
     */
    public String get(int index);
}
