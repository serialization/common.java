package ogss.common.java.internal;

/**
 * Known Container Constructor. This is essentially a product of a container name and a function that constructs the
 * container from TBN. As with other known entities, there is a constructor for both known and unknown perspectives. As
 * always, known is more efficient.
 * 
 * @note construction is done by the user.
 * @author Timm Felden
 * @TODO should be int: kind|2 + sifaID|15 + sifaID|15; typeID in terms of the original state (there is SIFA anyway)
 *       (note: has to include low IDs)
 */
public final class KCC {

    public KCC(String name, int kind, String base) {
        this.name = name;
        this.kind = kind;
        b1 = base;
        b2 = null;
    }

    public KCC(String name, String k, String v) {
        this.name = name;
        kind = 3;
        b1 = k;
        b2 = v;
    }

    public final String name;
    public final int kind;
    public final String b1;
    public final String b2;
}
