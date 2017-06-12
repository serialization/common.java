package de.ust.skill.common.java.api;

import java.util.Iterator;
import java.util.stream.Stream;

import de.ust.skill.common.java.internal.SkillObject;

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
     * @return the skill file owning this access
     */
    public SkillFile owner();

    /**
     * @return the skill name of the super type, if it exists
     */
    public String superName();

    /**
     * @return an iterator over all fields of T
     */
    public Iterator<? extends FieldDeclaration<?>> fields();
    /**
     * @return an iterator over all fields of T including fields declared in super types
     */
    public Iterator<? extends FieldDeclaration<?>> allFields();

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
