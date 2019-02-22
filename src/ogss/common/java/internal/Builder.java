package ogss.common.java.internal;

/**
 * Builder for new instances of the pool.
 * 
 * @author Timm Felden
 * @todo revisit implementation after the pool is completely implemented. Having an instance as constructor argument is
 *       questionable.
 */
public abstract class Builder<T extends Obj> {
    protected Pool<T> p;
    public final T self;

    protected Builder(Pool<T> pool, T self) {
        this.p = pool;
        this.self = self;
    }

    /**
     * registers the object and invalidates the builder
     * 
     * @note abstract to work around JVM bug
     * @return the created object
     */
    public final T make() {
        p.add(self);
        p = null; // invalidate to prevent duplicate registration
        return self;
    }
}
