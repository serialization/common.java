package de.ust.skill.common.java.internal.fieldTypes;

public final class ConstantI32 extends ConstantIntegerType<Integer> {

    /**
     * @see SKilL V1.0 reference manual §G
     */
    public static final int typeID = 2;

    public final int value;

    public ConstantI32(int value) {
        super(typeID);
        this.value = value;
    }

    @Override
    public Integer value() {
        return value;
    }

    @Override
    public String toString() {
        return String.format("const i32 = %08X", value);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ConstantI32)
            return value == ((ConstantI32) obj).value;
        return false;
    }
}
