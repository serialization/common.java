package ogss.common.java.internal;

import java.util.HashMap;

import ogss.common.java.internal.exceptions.ParseException;
import ogss.common.streams.FileInputStream;

/**
 * Create an empty state. The approach here is different from the generated initialization code in SKilL to reduce the
 * amount of generated code.
 * 
 * @author Timm Felden
 */
final public class Creator extends StateInitializer {

    public Creator(FileInputStream in, Class<Pool<?, ?>>[] knownClasses, String[] classNames) {
        super(in, knownClasses, classNames);

        guard = "";

        try {
            // Create Classes
            for (Class<Pool<?, ?>> cls : knownClasses) {
                Pool<?, ?> p = (Pool<?, ?>) cls.getConstructors()[0].newInstance(classes, null);
                classes.add(p);
                typeByName.put(p.name, p);
                Strings.add(p.name);
            }

            // TODO Create Hulls

            // Create Fields
            for (Pool<?, ?> p : classes) {
                int ki = 0;
                for (String f : p.knownFields) {
                    Strings.add(f);
                    try {
                        p.KFC[ki++].getConstructor(HashMap.class, int.class, p.getClass()).newInstance(typeByName, nextFieldID++, p);
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
                Pool<?, ?> n, p = classes.get(i + 1);
                // propagate information in reverse order
                // i is the pool where next is set, hence we skip the last pool
                do {
                    n = p;
                    p = classes.get(i);

                    // by compactness, if n has a super pool, p is the previous pool
                    if (null != n.superPool) {
                        // raw cast, because we cannot prove here that it is B, because wo do not want to introduce a
                        // function as quantifier which would not provide any benefit anyway
                        p.next = (Pool) n;
                    }

                } while (--i >= 0);
            }
        }
    }

}
