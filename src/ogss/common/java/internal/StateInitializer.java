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

    Class<Pool<?, ?>>[] knownClasses;

    String[] classNames;

    /**
     * The next global field ID. Note that this ID does not correspond to the ID used in the file about to be read but
     * to an ID that would be used if it were written.
     * 
     * @note to make this work as intended, merging known fields into the dataFields array has to be done while reading
     *       F.
     */
    protected int nextFieldID = 0;

    StateInitializer(FileInputStream in, Class<Pool<?, ?>>[] knownClasses, String[] classNames) {
        this.knownClasses = knownClasses;
        this.classNames = classNames;

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
