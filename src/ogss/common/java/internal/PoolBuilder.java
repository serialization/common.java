package ogss.common.java.internal;

public abstract class PoolBuilder {

    protected PoolBuilder(int sifaSize) {
        this.sifaSize = sifaSize;
    }

    /**
     * The size of sifa as constructed by this PoolBuilder.
     */
    final int sifaSize;

    /**
     * In contrast to C++, we will directly return an array of strings. This is a sane solution because of String.intern
     * rules for literal strings in java. In consequence, by-name access is not required.
     * 
     * @return The lexically sorted array of strings returned as name of a type, field or enum constant.
     */
    protected abstract String[] literals();

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
