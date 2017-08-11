package de.ust.skill.common.java.internal;

import java.io.IOException;
import java.util.Collection;

import de.ust.skill.common.java.api.GeneralAccess;
import de.ust.skill.common.java.internal.fieldTypes.Annotation;
import de.ust.skill.common.jvm.streams.InStream;
import de.ust.skill.common.jvm.streams.OutStream;

/**
 * Holds interface instances. Serves as an API realization. Ensures correctness
 * of reflective type system.
 * 
 * @note in this case, the super type is annotation
 * @note unfortunately, one cannot prove that T extends SkillObject. Hence, we
 *       cannot inherit from Access<T>
 *
 * @note typing in this implementation is intentionally incorrect, because java
 *       does not permit interfaces to inherit from classes
 * 
 * @author Timm Felden
 */
final public class UnrootedInterfacePool<T> extends FieldType<T> implements GeneralAccess<T> {

    final private String name;
    final private Annotation superType;
    /**
     * @note the Java type system seems to be to weak to prove the correct type
     *       [? extends T, ?]
     */
    final private StoragePool<SkillObject, SkillObject>[] realizations;

    /**
     * Construct an interface pool.
     * 
     * @note realizations must be in type order
     * @note realizations must be of type StoragePool<? extends T, ?>
     */
    @SuppressWarnings("unchecked")
    public UnrootedInterfacePool(String name, Annotation superPool, StoragePool<?, ?>... realizations) {
        super(superPool.typeID());
        this.name = name;
        this.superType = superPool;
        this.realizations = (StoragePool<SkillObject, SkillObject>[]) realizations;
    }

    @Override
    public int size() {
        int rval = 0;
        for (StoragePool<SkillObject, SkillObject> p : realizations) {
            rval += p.size();
        }
        return rval;
    }

    @Override
    final public InterfaceIterator<T> iterator() {
        return new InterfaceIterator<T>(realizations);
    }

    @Override
    final public String name() {
        return name;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T readSingleField(InStream in) {
        return (T) superType.readSingleField(in);
    }

    @Override
    public long calculateOffset(Collection<T> xs) {
        return superType.calculateOffset(cast(xs));
    }

    @Override
    public final long singleOffset(T x) {
        return superType.singleOffset((SkillObject) x);
    }

    @Override
    public void writeSingleField(T data, OutStream out) throws IOException {
        superType.writeSingleField((SkillObject) data, out);
    }

    /**
     * hide cast that is required because interfaces do not inherit from classes
     */
    @SuppressWarnings("unchecked")
    private static final <V, U> U cast(V x) {
        return (U) x;
    }

    public Annotation getType() {
        return superType;
    }

    @Override
    public String toString() {
        return name;
    }
}
