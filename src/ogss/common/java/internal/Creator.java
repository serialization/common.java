package ogss.common.java.internal;

import ogss.common.java.internal.fieldDeclarations.AutoField;
import ogss.common.java.internal.fieldTypes.ArrayType;
import ogss.common.java.internal.fieldTypes.ListType;
import ogss.common.java.internal.fieldTypes.MapType;
import ogss.common.java.internal.fieldTypes.SetType;
import ogss.common.java.internal.fieldTypes.SingleArgumentType;

/**
 * Create an empty state. The approach here is different from the generated initialization code in SKilL to reduce the
 * amount of generated code.
 * 
 * @author Timm Felden
 */
final public class Creator extends StateInitializer {

    Creator(PoolBuilder pb) {
        super(null, pb);

        guard = "";

        try {
            // Create Classes
            for (int i = 0; null != pb.name(i); i++) {
                Pool<?> p = pb.make(i, classes, null);
                SIFA[nsID++] = p;
                classes.add(p);
                TBN.put(p.name, p);
                Strings.add(p.name);
            }

            // Execute known container constructors
            {
                int tid = 10 + classes.size();
                int kcc;
                for (int i = 0; -1 != (kcc = pb.kcc(i)); i++) {
                    HullType<?> r;
                    switch ((kcc >> 30) & 3) {
                    case 0:
                        r = new ArrayType<>(tid++, SIFA[kcc & 0x7FFF]);
                        break;
                    case 1:
                        r = new ListType<>(tid++, SIFA[kcc & 0x7FFF]);
                        break;
                    case 2:
                        r = new SetType<>(tid++, SIFA[kcc & 0x7FFF]);
                        break;

                    case 3:
                        r = new MapType<>(tid++, SIFA[kcc & 0x7FFF], SIFA[(kcc >> 15) & 0x7FFF]);
                        break;

                    default:
                        throw new Error(); // dead
                    }
                    SIFA[nsID++] = r;
                    TBN.put(r.toString(), r);
                    r.fieldID = nextFieldID++;
                    containers.add(r);
                }
            }

            // Create Fields
            for (Pool<?> p : classes) {
                String f;
                for (int i = 0; null != (f = p.KFN(i)); i++) {
                    Strings.add(f);

                    final FieldDeclaration<?, ?> fd = p.KFC(i, SIFA, nextFieldID);

                    if (!(fd instanceof AutoField)) {
                        nextFieldID++;

                        // increase maxDeps
                        if (fd.type instanceof HullType<?>) {
                            ((HullType<?>) fd.type).maxDeps++;
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new Error("failed to create state", e);
        }

        fixContainerMD();

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
