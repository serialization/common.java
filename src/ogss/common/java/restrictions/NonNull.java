package ogss.common.java.restrictions;

import ogss.common.java.api.OGSSException;

/**
 * A nonnull restricition. It will ensure that field data is non null.
 * 
 * @author Timm Felden
 */
public class NonNull<T> implements FieldRestriction<T> {
    private static final NonNull<?> instance = new NonNull<>();

    private NonNull() {
    }

    public static NonNull<?> get() {
        return instance;
    }

    @Override
    public void check(T value) throws OGSSException {
        if (value == null)
            throw new OGSSException("Null value violates @NonNull.");
    }
}
