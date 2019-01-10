package ogss.common.java.restrictions;

import ogss.common.java.api.SkillException;

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
    public void check(T value) throws SkillException {
        if (value == null)
            throw new SkillException("Null value violates @NonNull.");
    }
}
