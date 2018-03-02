package de.ust.skill.common.java.api;

import java.util.Iterator;
import java.util.stream.Stream;

import de.ust.skill.common.java.internal.FieldIterator;
import de.ust.skill.common.java.internal.SkillObject;
import de.ust.skill.common.java.internal.StaticDataIterator;
import de.ust.skill.common.java.internal.StaticFieldIterator;

/**
 * Access to class type <T>.
 * 
 * @author Timm Felden
 */
public interface Access<T extends SkillObject> extends GeneralAccess<T> {
    
    /**
     * @return a stream over all T's managed by this access
     */
    public Stream<T> stream();

    /**
     * @return a type ordered Container iterator over all instances of T
     * @note do not invoke this function, if you do not know what "type order"
     *       means
     */
    public Iterator<T> typeOrderIterator();
    
    /**
     * @return an iterator over all instances of the type represented by this access not including instances of subtypes
     */
    public StaticDataIterator<T> staticInstances();

    /**
     * @return the skill file owning this access
     */
    public SkillFile owner();

    /**
     * @return the skill name of the super type, if it exists
     */
    public String superName();

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
     * @throws SkillException
     *             if no instance can be created. This is either caused by
     *             restrictions, such as @singleton, or by invocation on unknown
     *             types, which are implicitly unmodifiable in this
     *             SKilL-implementation.
     */
    public T make() throws SkillException;
}
