package ogss.common.java.api;

/**
 * An attribute of a node, i.e. a field value that is not a pointer.
 * 
 * @author Timm Felden
 */
public final class Attribute {

    public Attribute(String name, boolean isTransient, Object value) {
        this.name = name;
        this.isTransient = isTransient;
        this.value = value;
    }

    public final boolean isTransient;

    public final String name;

    public final Object value;

    @Override
    public final String toString() {
        return value.toString();
    }
}
