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

    /**
     * @return the name of the pool corresponding to the argument known id; return null if not a valid id
     */
    protected abstract String name(int id);

    /**
     * @param pass
     *            superDef, if known; if a check is required, is has to be performed by the caller!
     * @return an instance of the pool corresponding to the argument known id
     */
    protected abstract Pool<?> make(int id, ArrayList<Pool<?>> classes, Pool<?> superDef);
}
