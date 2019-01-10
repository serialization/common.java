package ogss.common.java.internal;

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
            for (Class<Pool<?, ?>> cls : knownClasses) {
                Pool<?, ?> p = (Pool<?, ?>) cls.getConstructors()[0].newInstance(classes, null);
                classes.add(p);
                poolByName.put(p.name, p);
                Strings.add(p.name);
                for (String f : p.knownFields) {
                    Strings.add(f);
                    p.addKnownField(f, Strings, Annotation);
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
                        // function as quntifier which would not provide any benefit anyway
                        p.next = (Pool) n;
                    }

                } while (--i >= 0);
            }
        }
    }

}
