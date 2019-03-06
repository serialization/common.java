package ogss.common.java.internal;

/**
 * Builder for new instances of the pool.
 * 
 * @author Timm Felden
 * @note the generic co-hierarchy is used to compress the builder hierarchy where possible
 */
public class Builder<T extends Obj> {
    private Pool<T> p;
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
