package ogss.common.java.restrictions;

import ogss.common.java.api.OGSSException;

/**
 * A restriction that can be applied to a field.
 * 
 * @author Timm Felden
 * @param <T>
 *            The java type of the field.
 */
public interface FieldRestriction<T> {
    /**
     * Checks a value and throws an exception in case of error. We prefer the
     * exception throwing mechanism over return values, because we expect checks
     * to fail almost never.
     * 
     * @param value
     *            the value to be checked
     */
    public void check(T value) throws OGSSException;
}
