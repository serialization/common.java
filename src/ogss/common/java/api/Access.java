package ogss.common.java.api;

import java.util.stream.Stream;

import ogss.common.java.internal.FieldIterator;
import ogss.common.java.internal.Obj;
import ogss.common.java.internal.Pool;
import ogss.common.java.internal.StaticDataIterator;
import ogss.common.java.internal.StaticFieldIterator;
import ogss.common.java.internal.TypeOrderIterator;

/**
 * Access to class type <T>.
 * 
 * @author Timm Felden
 */
public interface Access<T extends Obj> extends GeneralAccess<T> {

    /**
     * @return a stream over all T's managed by this access
     */
    public Stream<T> stream();

    /**
     * @return a type ordered Container iterator over all instances of T
     * @note do not invoke this function, if you do not know what "type order" means
     */
    public default TypeOrderIterator<T> typeOrderIterator() {
        return new TypeOrderIterator<>((Pool<T>) this);
    }

    /**
     * @return an iterator over all instances of the type represented by this access not including instances of subtypes
     */
    public StaticDataIterator<T> staticInstances();

    /**
     * @return the super type, if it exists
     */
    public Access<? super T> superType();

    /**
     * @return an iterator over fields declared by T
     */
    public StaticFieldIterator fields();

    /**
     * @return an iterator over all fields of T including fields declared in super types
     */
    public FieldIterator allFields();

    /**
     * @return a new T instance with default field values
     * @throws OGSSException
     *             If no instance can be created. This is either caused by attributes, such as @singleton, or by
     *             invocation on unknown types, which are implicitly unmodifiable in this OGSS-implementation.
     */
    public T make() throws OGSSException;
}
