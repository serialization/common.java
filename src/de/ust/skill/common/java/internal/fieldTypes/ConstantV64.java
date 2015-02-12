package de.ust.skill.common.java.internal.fieldTypes;

public final class ConstantV64 extends ConstantIntegerType<Long> {
    public final long value;

    public ConstantV64(long value) {
        super(4);
        this.value = value;
    }

    @Override
    public Long value() {
        return value;
    }

    @Override
    public String toString() {
        return String.format("const v64 = %016X", value);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ConstantV64)
            return value == ((ConstantV64) obj).value;
        return false;
    }
}
