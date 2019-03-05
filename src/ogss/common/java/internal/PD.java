package ogss.common.java.internal;

/**
 * A PoolDescriptor holds specification-provided information required to construct a pool.
 * 
 * @author Timm Felden
 */
public class PD {
    final String name;

    /**
     * The name of the super pool for known PDs with super pool and null otherwise
     */
    final String superName;

    /**
     * the real thing super pool
     * 
     * @note the state initialzizer has to turn this into an actual pool, if it is not known anyway
     * @note pool allocation has to be synchronized on this on static PDs (known pools)
     */
    Pool<?, ?> superPool;

    final Class<?> t;
    final Class<?> sub;
    final Class<?> builder;

    final String[] KFN;
    final Class<?>[] KFC;

    private static final String[] noKFN = new String[0];
    private static final Class<?>[] noKFC = new Class[0];

    final int autoFields;

    /**
     * known pools
     */
    public PD(String name, String superName, Class<?> t, Class<?> sub, Class<?> builder, String[] KFN, Class<?>[] KFC,
            int autoFields) {
        this.name = name;
        this.superName = superName;

        this.t = t;
        this.sub = sub;
        this.builder = builder;

        this.KFN = null == KFN ? noKFN : KFN;
        this.KFC = null == KFC ? noKFC : KFC;
        this.autoFields = autoFields;
    }

    /**
     * unknown pools
     */
    public PD(String name, Class<?> t, Pool<?, ?> superPool) {
        this.name = name;
        this.superName = null;
        this.superPool = superPool;

        this.t = t;
        this.sub = t;
        this.builder = null;

        this.KFN = noKFN;
        this.KFC = noKFC;
        this.autoFields = 0;
    }
}
