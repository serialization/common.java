package ogss.common.java.internal.fieldDeclarations;

import ogss.common.jvm.streams.MappedInStream;

/**
 * This trait marks ignored fields.
 * 
 * @author Timm Felden
 */
public interface IgnoredField {
    public default void read(MappedInStream in) {
        // does nothing, the field is ignored

        // @note maybe we have to revise this behavior for correct
        // implementation of write
    }
}
