package de.ust.skill.common.java.internal.fieldTypes;

public final class ConstantI16 extends ConstantIntegerType<Short> {

    /**
     * @see SKilL V1.0 reference manual §G
     */
    public static final int typeID = 1;

    public final short value;

    public ConstantI16(short value) {
        super(typeID);
        this.value = value;
    }

    @Override
    public Short value() {
        return value;
    }

    @Override
    public String toString() {
        return String.format("const i16 = %04X", value);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ConstantI16)
            return value == ((ConstantI16) obj).value;
        return false;
    }
}
