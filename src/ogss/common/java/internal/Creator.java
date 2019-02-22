package ogss.common.java.internal;

import java.util.HashMap;

import ogss.common.java.api.SkillException;
import ogss.common.java.internal.exceptions.ParseException;
import ogss.common.java.internal.fieldDeclarations.AutoField;
import ogss.common.java.internal.fieldTypes.ArrayType;
import ogss.common.java.internal.fieldTypes.ListType;
import ogss.common.java.internal.fieldTypes.MapType;
import ogss.common.java.internal.fieldTypes.SetType;
import ogss.common.streams.FileInputStream;

/**
 * Create an empty state. The approach here is different from the generated initialization code in SKilL to reduce the
 * amount of generated code.
 * 
 * @author Timm Felden
 */
final public class Creator extends StateInitializer {

    public Creator(FileInputStream in, Class<Pool<?>>[] knownClasses, String[] classNames, KCC[] kccs) {
        super(in, knownClasses, classNames, kccs);

        guard = "";

        try {
            // Create Classes
            for (int i = 0; i < knownClasses.length; i++) {
                Pool<?> p = (Pool<?>) knownClasses[i].getConstructors()[0].newInstance(classes, null);
                SIFA[i] = p;
                classes.add(p);
                typeByName.put(p.name, p);
                Strings.add(p.name);
            }

            // Execute known container constructors
            {
                int tid = 10 + classes.size();
                for (KCC c : kccs) {
                    HullType<?> r;
                    switch (c.kind) {
                    case 0:
                        r = new ArrayType<>(tid++, typeByName.get(c.b1));
                        break;
                    case 1:
                        r = new ListType<>(tid++, typeByName.get(c.b1));
                        break;
                    case 2:
                        r = new SetType<>(tid++, typeByName.get(c.b1));
                        break;

                    case 3:
                        r = new MapType<>(tid++, typeByName.get(c.b1), typeByName.get(c.b2));
                        break;

                    default:
                        throw new SkillException("Illegal container constructor ID: " + c.kind);
                    }
                    typeByName.put(r.toString(), r);
                    r.fieldID = nextFieldID++;
                    containers.add(r);
                }
            }

            // Create Fields
            for (Pool<?> p : classes) {
                int ki = 0;
                int af = -1;
                for (String f : p.KFN) {
                    Strings.add(f);
                    try {
                        final Class<?> cls = p.KFC[ki++];
                        if (cls.getSuperclass() == AutoField.class) {
                            cls.getConstructor(HashMap.class, int.class, p.getClass()).newInstance(typeByName, af--, p);
                        } else {
                            cls.getConstructor(HashMap.class, int.class, p.getClass()).newInstance(typeByName,
                                    nextFieldID++, p);
                        }
                    } catch (Exception e) {
                        throw new ParseException(in, e, "Failed to instantiate known field " + p.name + "." + f);
                    }
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
