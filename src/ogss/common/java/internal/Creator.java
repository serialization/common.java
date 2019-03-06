package ogss.common.java.internal;

import ogss.common.java.api.SkillException;
import ogss.common.java.internal.fieldDeclarations.AutoField;
import ogss.common.java.internal.fieldTypes.ArrayType;
import ogss.common.java.internal.fieldTypes.ListType;
import ogss.common.java.internal.fieldTypes.MapType;
import ogss.common.java.internal.fieldTypes.SetType;

/**
 * Create an empty state. The approach here is different from the generated initialization code in SKilL to reduce the
 * amount of generated code.
 * 
 * @author Timm Felden
 */
final public class Creator extends StateInitializer {

    Creator(int sifaSize, PoolBuilder pb, KCC[] kccs) {
        super(null, sifaSize, kccs);

        guard = "";

        try {
            // Create Classes
            for (int i = 0; null != pb.name(i); i++) {
                Pool<?> p = pb.make(i, classes, null);
                SIFA[i] = p;
                classes.add(p);
                TBN.put(p.name, p);
                Strings.add(p.name);
            }

            // Execute known container constructors
            {
                int tid = 10 + classes.size();
                for (KCC c : kccs) {
                    HullType<?> r;
                    switch (c.kind) {
                    case 0:
                        r = new ArrayType<>(tid++, TBN.get(c.b1));
                        break;
                    case 1:
                        r = new ListType<>(tid++, TBN.get(c.b1));
                        break;
                    case 2:
                        r = new SetType<>(tid++, TBN.get(c.b1));
                        break;

                    case 3:
                        r = new MapType<>(tid++, TBN.get(c.b1), TBN.get(c.b2));
                        break;

                    default:
                        throw new SkillException("Illegal container constructor ID: " + c.kind);
                    }
                    TBN.put(r.toString(), r);
                    r.fieldID = nextFieldID++;
                    containers.add(r);
                }
            }

            // Create Fields
            for (Pool<?> p : classes) {
                int af = -1;
                String f;
                for (int i = 0; null != (f = p.KFN(i)); i++) {
                    Strings.add(f);

                    if (p.KFC(i, TBN, af, nextFieldID) instanceof AutoField)
                        af--;
                    else
                        nextFieldID++;
                }
            }
        } catch (Exception e) {
            throw new Error("failed to create state", e);
        }

        // set next for all pools
        {
            int i = classes.size() - 2;
            if (i >= 0) {
                Pool<?> n, p = classes.get(i + 1);
                // propagate information in reverse order
                // i is the pool where next is set, hence we skip the last pool
                do {
                    n = p;
                    p = classes.get(i);

                    // by compactness, if n has a super pool, p is the previous pool
                    if (null != n.superPool) {
                        // raw cast, because we cannot prove here that it is B, because wo do not want to introduce a
                        // function as quantifier which would not provide any benefit anyway
                        p.next = n;
                    }

                } while (--i >= 0);
            }
        }
    }

}
