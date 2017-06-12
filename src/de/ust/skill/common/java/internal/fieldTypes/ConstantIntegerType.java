package de.ust.skill.common.java.internal.fieldTypes;

import java.io.IOException;
import java.util.Collection;

import de.ust.skill.common.java.internal.FieldType;
import de.ust.skill.common.jvm.streams.InStream;
import de.ust.skill.common.jvm.streams.OutStream;

/**
 * Constant Integers.
 * 
 * @author Timm Felden
 */
public abstract class ConstantIntegerType<T> extends FieldType<T> {
    protected ConstantIntegerType(int typeID) {
        super(typeID);
    }

    public abstract T value();

    public final void expect(T arg) {
        assert value() == arg;
    }

    @Override
    public final T readSingleField(InStream in) {
        return value();
    }

    @Override
    final public long calculateOffset(Collection<T> xs) {
        // nothing to do
        return 0;
    }

    @Override
    final public long singleOffset(T x) {
        // nothing to do
        return 0;
    }

    @Override
    final public void writeSingleField(T data, OutStream out) throws IOException {
        // nothing to do
    }
}
