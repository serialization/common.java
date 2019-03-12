package ogss.common.java.internal;

/**
 * Follow the subtyping oriented variant of EnumProxies by Sarah Stieß. In contrast to her solution, we will only
 * instantiate this class directly if T is not contained in the tool specification.
 * 
 * @author Timm Felden
 */
public final class EnumProxy<T extends Enum<T>> {
    /**
     * Points to the target enum value, if it is part of the tool specification.
     * 
     * @note null iff the target is not part of the tool specification.
     */
    public final T target;

    /**
     * The pool owning this enum value. It can be used to discover other enum values contained in this file.
     */
    public final EnumPool<T> owner;

    /**
     * The name of this enum value. Names are stable.
     */
    public final String name;

    /**
     * The ID of this enum value. IDs are not stable as they depend on the input file.
     */
    public final int id;

    protected EnumProxy(T target, EnumPool<T> pool, String name, int id) {
        this.target = target;
        this.owner = pool;
        this.name = name;
        this.id = id;
    }
}
