package ogss.common.java.api;

import java.util.Collection;

/**
 * Provides access to Strings in a file.
 * 
 * @note As for Java, Strings in OGSS are special in that they are immutable, equivalent by image and eventually
 *       implicitly unified.
 * @author Timm Felden
 */
public interface StringAccess extends Collection<String>, GeneralAccess<String> {
}
