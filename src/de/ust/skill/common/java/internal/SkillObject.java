package de.ust.skill.common.java.internal;

import java.io.Serializable;

import de.ust.skill.common.java.api.FieldDeclaration;
import de.ust.skill.common.java.api.SkillFile;

/**
 * The root of the hierarchy of instances of skill user types. Annotations can
 * store arbitrary objects, thus this type has to exist explicitly.
 * 
 * @author Timm Felden
 * @note This type definition is in internal, because we have to protect
 *       setSkillID from the user
 */
// TODO create a builder for skill objects
public abstract class SkillObject implements Serializable {

    /**
     * @note Java Serialization is only usable, if specification is both,
     *       complete and without any distributed/onDemand fields. Both cases
     *       would cause unknown decomposed field information to float around
     *       without any means of the Java Serializer to know how to recompose
     *       them correctly. For change-tolerant serialization use SKilL. This
     *       mechanism has been implemented for the purpose of comparison only.
     */
    private static final long serialVersionUID = 0xDecaFade00005c11L;

    /**
     * The constructor is protected to ensure that users do not break states
     * accidentally
     */
    protected SkillObject(long skillID) {
        this.skillID = skillID;
    }

    /**
     * @return the skill name of this type
     */
    public abstract String skillName();

    /**
     * -1 for new objects<br>
     * 0 for deleted objects<br>
     * everything else is the ID of an object inside of a file
     */
    transient protected long skillID;

    /**
     * @return whether the object has been deleted
     */
    public boolean isDeleted() {
        return 0 == skillID;
    }

    /**
     * Do not rely on skill ID if you do not know exactly what you are doing.
     */
    public final long getSkillID() {
        return skillID;
    }

    /**
     * reflective setter
     *
     * @param field
     *            a field declaration instance as obtained from the storage
     *            pools iterator
     * @param value
     *            the new value of the field
     * @note if field is not a field of this.type, then anything may happen
     */
    public final <T> void set(de.ust.skill.common.java.api.FieldDeclaration<T> field, T value) {
        field.set(this, value);
    }

    /**
     * reflective getter
     *
     * @param field
     *            a field declaration instance as obtained from the storage
     *            pools iterator
     * @note if field is not a field of this.type, then anything may happen
     * @note the second type parameter of field has to be this.type.
     *       Unfortunately Java wont let us override the type parameter on each
     *       overload, although this pattern would automagically make everything
     *       work as intended and the user would always know whether using a
     *       field declaration on a specific instance would work well.
     */
    public final <T> T get(de.ust.skill.common.java.api.FieldDeclaration<T> field) {
        return field.get(this);
    }

    /**
     * potentially expensive but more pretty representation of this instance.
     */
    public final String prettyString(SkillFile sf) {
        StringBuilder sb = new StringBuilder("Age(this: ").append(this);
        StoragePool<?, ?> p = ((SkillState) sf).poolByName.get(skillName());
        printFs(p.fields(), sb);
        return sb.append(")").toString();
    }

    // provides required extra type quantification
    private final <T extends SkillObject> void printFs(FieldIterator fieldIterator, StringBuilder sb) {
        while (fieldIterator.hasNext()) {
            FieldDeclaration<?> f = fieldIterator.next();
            sb.append(", ").append(f.name()).append(": ").append(f.get(this));
        }
    }

    public static final class SubType extends SkillObject implements NamedType {
        /**
         * should not happen
         */
        private static final long serialVersionUID = 3283783094243102233L;

        transient private final StoragePool<?, ?> τPool;

        SubType(StoragePool<?, ?> τPool, long skillID) {
            super(skillID);
            this.τPool = τPool;
        }

        @Override
        public StoragePool<?, ?> τPool() {
            return τPool;
        }

        @Override
        public String toString() {
            return skillName() + "#" + skillID;
        }

        @Override
        public String skillName() {
            return τPool.name;
        }
    }
}
