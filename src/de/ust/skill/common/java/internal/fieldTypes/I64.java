package de.ust.skill.common.java.internal.fieldTypes;

import java.io.IOException;
import java.util.Collection;

import de.ust.skill.common.jvm.streams.InStream;
import de.ust.skill.common.jvm.streams.OutStream;

public final class I64 extends IntegerType<Long> {
    private final static I64 instance = new I64();

    public static I64 get() {
        return instance;
    }

    private I64() {
        super(10);
    }

    @Override
    public Long readSingleField(InStream in) {
        return in.i64();
    }

    @Override
    public long calculateOffset(Collection<Long> xs) {
        return 8 * xs.size();
    }

    @Override
    public long singleOffset(Long x) {
        return 8L;
    }

    @Override
    public void writeSingleField(Long target, OutStream out) throws IOException {
        out.i64(target);
    }

    @Override
    public String toString() {
        return "i64";
    }
}
