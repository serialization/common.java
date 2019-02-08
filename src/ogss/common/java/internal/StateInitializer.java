package ogss.common.java.internal;

import java.util.ArrayList;
import java.util.HashMap;

import ogss.common.java.internal.fieldTypes.AnyRefType;
import ogss.common.streams.FileInputStream;

/**
 * Initializes a state. One of Creator, Parser, SequentialParser.
 * 
 * @author Timm Felden
 */
public abstract class StateInitializer {
    final FileInputStream in;

    // guard from file
    String guard;

    // strings
    final StringPool Strings;

    // types
    final ArrayList<Pool<?, ?>> classes;
    final HashMap<String, ByRefType<?>> typeByName = new HashMap<>();
    final AnyRefType Annotation;

    final Class<Pool<?, ?>>[] knownClasses;
    /**
     * State Initialization of Fields Array. In C++, it should be possible to memcpy this array into the first field to
     * achieve state initialization.
     * 
     * @note invariant: ∀i. SIFA[i].getClass == knownClasses[i]
     * 
     * @note this is essentially the SKilL/Java large spec passing mode, except that the name binding happens implicitly
     */
    public final Pool<?, ?>[] SIFA;

    final String[] classNames;

    /**
     * The next global field ID. Note that this ID does not correspond to the ID used in the file about to be read but
     * to an ID that would be used if it were written.
     * 
     * @note to make this work as intended, merging known fields into the dataFields array has to be done while reading
     *       F.
     * @note ID 0 is reserved for the String hull which is always present
     */
    protected int nextFieldID = 1;

    StateInitializer(FileInputStream in, Class<Pool<?, ?>>[] knownClasses, String[] classNames) {
        this.knownClasses = knownClasses;
        this.classNames = classNames;
        SIFA = new Pool[knownClasses.length];

        this.in = in;
        Strings = new StringPool(in);

        classes = new ArrayList<>(knownClasses.length);

        Annotation = new AnyRefType(classes, typeByName);

        typeByName.put(Annotation.name(), Annotation);
        typeByName.put(Strings.name(), Strings);
    }

    /**
     * Called by the concrete state before returning from the constructor to ensure that potentially running parallel
     * tasks finished.
     */
    public void awaitResults() {
        // nothing by default
    }
}
