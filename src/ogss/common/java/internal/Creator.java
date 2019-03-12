package ogss.common.java.internal;

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

    Creator(PoolBuilder pb) {
        super(null, pb);

        guard = "";

        try {
            // Create Classes
            {
                int index = 0;
                int THH = 0;
                // the index of the next known class at index THH
                // @note to self: in C++ this should be string*[32]
                final int[] nextID = new int[32];
                String nextName = pb.name(0);

                Pool<?> p = null, last = null;
                while (null != nextName) {
                    if (0 == THH) {
                        last = null;
                        p = pb.make(nextID[0]++, index++);
                    } else {
                        p = p.makeSub(nextID[THH]++, index++);
                    }

                    SIFA[nsID++] = p;
                    classes.add(p);
                    Strings.add(p.name);

                    // set next
                    if (null != last) {
                        last.next = p;
                    }
                    last = p;

                    // move to next pool
                    {
                        // try to move down to our first child
                        nextName = p.nameSub(nextID[++THH] = 0);

                        // move up until we find a next pool
                        while (null == nextName && THH != 1) {
                            p = p.superPool;
                            nextName = p.nameSub(nextID[--THH]);
                        }
                        // check at base type level
                        if (null == nextName) {
                            nextName = pb.name(nextID[--THH]);
                        }
                    }
                }
            }

            // Execute known container constructors
            int tid = 10 + classes.size();
            {
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
                    r.fieldID = nextFieldID++;
                    containers.add(r);
                }
            }

            // Construct known enums
            {
                int ki = 0;
                EnumPool<?> r;
                String nextName = pb.enumName(ki);
                // create remaining known enums
                while (null != nextName) {
                    r = new EnumPool(tid++, nextName, null, pb.enumMake(ki++));
                    Strings.add(r.name);
                    for (EnumProxy<?> n : r.values) {
                        Strings.add(n.name);
                    }
                    enums.add(r);
                    SIFA[nsID++] = r;
                    nextName = pb.enumName(ki);
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
    }

}
