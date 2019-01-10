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
    final HashMap<String, Pool<?, ?>> poolByName = new HashMap<>();
    final AnyRefType Annotation;

    Class<Pool<?, ?>>[] knownClasses;

    String[] classNames;

    StateInitializer(FileInputStream in, Class<Pool<?, ?>>[] knownClasses, String[] classNames) {
        this.knownClasses = knownClasses;
        this.classNames = classNames;

        this.in = in;
        Strings = new StringPool(in);

        classes = new ArrayList<>(knownClasses.length);

        Annotation = new AnyRefType(classes);
    }

    /**
     * Called by the concrete state before returning from the constructor to ensure that potentially running parallel
     * tasks finished.
     */
    public void awaitResults() {
        // nothing by default
    }
}
