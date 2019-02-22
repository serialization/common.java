package ogss.common.java.internal;

/**
 * The base of a type hierarchy. Contains optimized representations of data compared to sub pools.
 * 
 * @author Timm Felden
 * @param <T>
 */
public class BasePool<T extends Pointer> extends Pool<T, T> {

    /**
     * the owner is set once by the SkillState.finish method!
     */
    protected State owner = null;

    protected BasePool(int poolIndex, String name, String[] knownFields, Class<FieldDeclaration<?, T>>[] KFC,
            int autoFields) {
        super(poolIndex, name, null, knownFields, KFC, autoFields);
    }

    @Override
    public final State owner() {
        return owner;
    }
}
