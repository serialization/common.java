package de.ust.skill.common.java.internal.fieldTypes;

public final class ConstantI8 extends ConstantIntegerType<Byte> {
    
    /**
     * @see SKilL V1.0 reference manual §G
     */
    public static final int typeID = 0;
    
    public final byte value;

    public ConstantI8(byte value) {
        super(typeID);
        this.value = value;
    }

    @Override
    public Byte value() {
        return value;
    }

    @Override
    public String toString() {
        return String.format("const i8 = %02X", value);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ConstantI8)
            return value == ((ConstantI8) obj).value;
        return false;
    }
}
