package ogss.common.java.internal;

import java.util.ArrayList;

public abstract class PoolBuilder {

    /**
     * Find a super pool if only its name is known. This is called by generated constructors to allow correct
     * instantiation of types from the tool specification
     */
    @SuppressWarnings("unchecked")
    protected static <T extends Obj> Pool<? super T> find(ArrayList<Pool<?>> classes, String name) {
        if (name == null)
            return null;

        int i = classes.size();
        // perform reverse search, because it is likely the last seen pool
        while (--i >= 0) {
            Pool<?> r = classes.get(i);
            if (r.name == name)
                return (Pool<? super T>) r;
        }
        throw new Error("internal error");
    }

    protected PoolBuilder(int sifaSize) {
        this.sifaSize = sifaSize;
    }

    /**
     * The size of sifa as constructed by this PoolBuilder.
     */
    final int sifaSize;

    /**
     * Known Container Constructor. Coded as kind|2 + sifaID|15 + sifaID|15. The IDs are relative to SIFA rather than
     * udts (note: has to include low IDs, i.e. sifaID is a shifted index)
     * 
     * @return -1 if there are no more KCCs
     */
    protected abstract int kcc(int id);

    /**
     * @return the name of the pool corresponding to the argument known id; return null if not a valid id
     */
    protected abstract String name(int id);

    /**
     * Create a new base pool.
     * 
     * @return an instance of the pool corresponding to the argument known id
     */
    protected abstract Pool<?> make(int id, int index);

    /**
     * @return names of known enums in ascending order
     */
    protected abstract String enumName(int id);

    /**
     * @return values of known enums in ascending order
     */
    protected abstract Enum<?>[] enumMake(int id);
}
