package de.ust.skill.common.java.internal;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;

import de.ust.skill.common.java.api.SkillException;
import de.ust.skill.common.java.internal.fieldDeclarations.KnownField;
import de.ust.skill.common.java.internal.fieldTypes.Annotation;
import de.ust.skill.common.java.internal.fieldTypes.StringType;
import de.ust.skill.common.java.internal.fieldTypes.V64;
import de.ust.skill.common.java.internal.parts.Block;
import de.ust.skill.common.java.internal.parts.BulkChunk;
import de.ust.skill.common.java.internal.parts.SimpleChunk;
import de.ust.skill.common.jvm.streams.MappedInStream;
import de.ust.skill.common.jvm.streams.MappedOutStream;
import sun.misc.Unsafe;

public abstract class KnownDataField<T, Obj extends SkillObject> extends FieldDeclaration<T, Obj>
        implements KnownField<T, Obj> {

    protected KnownDataField(FieldType<T> type, String name, StoragePool<Obj, ? super Obj> owner) {
        super(type, name, owner);
    }

    final static Unsafe u;
    static {
        try {
            Field fu = Unsafe.class.getDeclaredField("theUnsafe");
            fu.setAccessible(true);
            u = (Unsafe) fu.get(null);
        } catch (Exception e) {
            throw new RuntimeException("this implementation of skill.common requires access to Unsafe", e);
        }
    }

    /**
     * @return Java Field used to access data via Unsafe
     */
    protected abstract Field javaField() throws Exception;

    /**
     * Dispatch on the type and create specialized read methods for each type.
     * Reads for ordinary references are handled by erasure. Containers are
     * handled via SKilL-reflection.
     */
    @Override
    protected final void rsc(SimpleChunk c, MappedInStream in) {
        final int id = type.typeID();
        // treat constants
        if (id <= 4)
            return;

        final long df;
        try {
            df = u.objectFieldOffset(javaField());
        } catch (Exception e) {
            throw new SkillException(e);
        }
        final SkillObject[] d = owner.data;
        switch (id) {

        case 5:
            rscAnnotation((int) c.bpo, (int) (c.bpo + c.count), in, df, d);
            return;

        case 14:
            rscString((int) c.bpo, (int) (c.bpo + c.count), in, df, d);
            return;

        case 6:
            rscBool((int) c.bpo, (int) (c.bpo + c.count), in, df, d);
            return;

        case 7:
            rscI8((int) c.bpo, (int) (c.bpo + c.count), in, df, d);
            return;
        case 8:
            rscI16((int) c.bpo, (int) (c.bpo + c.count), in, df, d);
            return;
        case 9:
        case 12:
            rscI32((int) c.bpo, (int) (c.bpo + c.count), in, df, d);
            return;
        case 10:
        case 13:
            rscI64((int) c.bpo, (int) (c.bpo + c.count), in, df, d);
            return;
        case 11:
            rscV64((int) c.bpo, (int) (c.bpo + c.count), in, df, d);
            return;

        case 15:
        case 17:
        case 18:
        case 19:
        case 20:
            rscContainer((int) c.bpo, (int) (c.bpo + c.count), in, df, d);
            return;

        default:
            rscRef((int) c.bpo, (int) (c.bpo + c.count), in, df, d);
            return;
        }
    }

    private final void rscContainer(int i, final int h, MappedInStream in, long df, SkillObject[] d) {
        for (; i != h; i++) {
            u.putObject(d[i], df, type.readSingleField(in));
        }
    }

    private final void rscBool(int i, final int h, MappedInStream in, long df, SkillObject[] d) {
        for (; i != h; i++) {
            u.putBoolean(d[i], df, in.bool());
        }
    }

    private final void rscI8(int i, final int h, MappedInStream in, long df, SkillObject[] d) {
        for (; i != h; i++) {
            u.putByte(d[i], df, in.i8());
        }
    }

    private final void rscI16(int i, final int h, MappedInStream in, long df, SkillObject[] d) {
        for (; i != h; i++) {
            u.putShort(d[i], df, in.i16());
        }
    }

    private final void rscI32(int i, final int h, MappedInStream in, long df, SkillObject[] d) {
        for (; i != h; i++) {
            u.putInt(d[i], df, in.i32());
        }
    }

    private final void rscI64(int i, final int h, MappedInStream in, long df, SkillObject[] d) {
        for (; i != h; i++) {
            u.putLong(d[i], df, in.i64());
        }
    }

    private final void rscV64(int i, final int h, MappedInStream in, long df, SkillObject[] d) {
        for (; i != h; i++) {
            u.putLong(d[i], df, in.v64());
        }
    }

    private final void rscRef(int i, final int h, MappedInStream in, long df, SkillObject[] d) {
        StoragePool<?, ?> t = ((StoragePool<?, ?>) type);
        for (; i != h; i++) {
            final long v = in.v64();
            if (0 != v)
                u.putObject(d[i], df, t.getByID(v));
        }
    }

    private final void rscString(int i, final int h, MappedInStream in, long df, SkillObject[] d) {
        StringPool t = owner.owner().strings;
        for (; i != h; i++) {
            final long v = in.v64();
            if (0 != v)
                u.putObject(d[i], df, t.get(v));
        }
    }

    private final void rscAnnotation(int i, final int h, MappedInStream in, long df, SkillObject[] d) {
        final Annotation t;
        if (type instanceof Annotation)
            t = (Annotation) type;
        else
            t = ((UnrootedInterfacePool<T>) type).getType();

        for (; i != h; i++) {
            u.putObject(d[i], df, t.readSingleField(in));
        }
    }

    /**
     * Defer reading to rsc by creating adequate temporary simple chunks.
     */
    @Override
    protected final void rbc(BulkChunk c, MappedInStream in) {
        ArrayList<Block> blocks = owner.blocks();
        int blockIndex = 0;
        final int endBlock = c.blockCount;
        while (blockIndex < endBlock) {
            Block b = blocks.get(blockIndex++);
            rsc(new SimpleChunk(-1, -1, b.bpo, b.count), in);
        }
    }

    /**
     * Dispatch on the type and create specialized offset methods for each type.
     * Offsets for ordinary references are handled by erasure. Containers are
     * handled via SKilL-reflection.
     */
    @Override
    protected final long osc(SimpleChunk c) {
        final int id = type.typeID();
        // treat constants
        if (id <= 4)
            return 0L;

        final long df;
        try {
            df = u.objectFieldOffset(javaField());
        } catch (Exception e) {
            throw new SkillException(e);
        }
        final SkillObject[] d = owner.data;
        switch (id) {

        case 5:
            return oscAnnotation((int) c.bpo, (int) (c.bpo + c.count), df, d);

        case 14:
            return oscString((int) c.bpo, (int) (c.bpo + c.count), df, d);

        case 6:
        case 7:
            return c.count;

        case 8:
            return c.count << 1;

        case 9:
        case 12:
            return c.count << 2;

        case 10:
        case 13:
            return c.count << 3;

        case 11:
            return oscV64((int) c.bpo, (int) (c.bpo + c.count), df, d);

        case 15:
        case 17:
        case 18:
        case 19:
        case 20:
            return oscContainer((int) c.bpo, (int) (c.bpo + c.count), df, d);

        default:
            return oscRef((int) c.bpo, (int) (c.bpo + c.count), df, d);
        }
    }

    private long oscV64(int i, final int h, final long df, final SkillObject[] d) {
        long r = 0L;
        for (; i != h; i++) {
            r += V64.singleV64Offset(u.getLong(d[i], df));
        }
        return r;
    }

    private long oscString(int i, final int h, final long df, final SkillObject[] d) {
        StringType t = (StringType) type;
        long r = 0L;
        for (; i != h; i++) {
            r += t.singleOffset((String) u.getObject(d[i], df));
        }
        return r;
    }

    private long oscAnnotation(int i, final int h, final long df, final SkillObject[] d) {
        final Annotation t;
        if (type instanceof Annotation)
            t = (Annotation) type;
        else
            t = ((UnrootedInterfacePool<T>) type).getType();

        long r = 0L;
        for (; i != h; i++) {
            r += t.singleOffset((SkillObject) u.getObject(d[i], df));
        }
        return r;
    }

    private long oscRef(int i, final int h, final long df, final SkillObject[] d) {
        long r = 0L;
        for (; i != h; i++) {
            SkillObject v = (SkillObject) u.getObject(d[i], df);
            r += null == v ? 1L : V64.singleV64Offset(v.skillID);
        }
        return r;
    }

    private long oscContainer(int i, final int h, final long df, final SkillObject[] d) {
        long r = 0L;
        for (; i != h; i++) {
            r += type.singleOffset((T) u.getObject(d[i], df));
        }
        return r;
    }

    /**
     * Defer reading to osc by creating adequate temporary simple chunks.
     */
    @Override
    protected final long obc(BulkChunk c) {
        long result = 0L;
        ArrayList<Block> blocks = owner.blocks();
        int blockIndex = 0;
        final int endBlock = c.blockCount;
        while (blockIndex < endBlock) {
            Block b = blocks.get(blockIndex++);
            result += osc(new SimpleChunk(-1, -1, b.bpo, b.count));
        }
        return result;
    }

    /**
     * Dispatch on the type and create specialized offset methods for each type.
     * Offsets for ordinary references are handled by erasure. Containers are
     * handled via SKilL-reflection.
     */
    @Override
    protected final void wsc(SimpleChunk c, MappedOutStream out) throws IOException {
        final int id = type.typeID();
        // treat constants
        if (id <= 4)
            return;

        final long df;
        try {
            df = u.objectFieldOffset(javaField());
        } catch (Exception e) {
            throw new SkillException(e);
        }
        final SkillObject[] d = owner.data;
        switch (id) {

        case 5:
            wscAnnotation((int) c.bpo, (int) (c.bpo + c.count), df, d, out);
            return;

        case 14:
            wscString((int) c.bpo, (int) (c.bpo + c.count), df, d, out);
            return;

        case 6:
            wscBool((int) c.bpo, (int) (c.bpo + c.count), df, d, out);
            return;

        case 7:
            wscI8((int) c.bpo, (int) (c.bpo + c.count), df, d, out);
            return;

        case 8:
            wscI16((int) c.bpo, (int) (c.bpo + c.count), df, d, out);
            return;

        case 9:
        case 12:
            wscI32((int) c.bpo, (int) (c.bpo + c.count), df, d, out);
            return;

        case 10:
        case 13:
            wscI64((int) c.bpo, (int) (c.bpo + c.count), df, d, out);
            return;

        case 11:
            wscV64((int) c.bpo, (int) (c.bpo + c.count), df, d, out);
            return;

        case 15:
        case 17:
        case 18:
        case 19:
        case 20:
            wscContainer((int) c.bpo, (int) (c.bpo + c.count), df, d, out);
            return;

        default:
            wscRef((int) c.bpo, (int) (c.bpo + c.count), df, d, out);
            return;
        }
    }

    private void wscBool(int i, final int h, final long df, final SkillObject[] d, final MappedOutStream out)
            throws IOException {
        for (; i != h; i++) {
            out.bool(u.getBoolean(d[i], df));
        }
    }

    private void wscI8(int i, final int h, final long df, final SkillObject[] d, final MappedOutStream out)
            throws IOException {
        for (; i != h; i++) {
            out.i8(u.getByte(d[i], df));
        }
    }

    private void wscI16(int i, final int h, final long df, final SkillObject[] d, final MappedOutStream out)
            throws IOException {
        for (; i != h; i++) {
            out.i16(u.getShort(d[i], df));
        }
    }

    private void wscI32(int i, final int h, final long df, final SkillObject[] d, final MappedOutStream out)
            throws IOException {
        for (; i != h; i++) {
            out.i32(u.getShort(d[i], df));
        }
    }

    private void wscI64(int i, final int h, final long df, final SkillObject[] d, final MappedOutStream out)
            throws IOException {
        for (; i != h; i++) {
            out.i64(u.getLong(d[i], df));
        }
    }

    private void wscV64(int i, final int h, final long df, final SkillObject[] d, final MappedOutStream out)
            throws IOException {
        for (; i != h; i++) {
            out.v64(u.getLong(d[i], df));
        }
    }

    private void wscContainer(int i, final int h, final long df, final SkillObject[] d, final MappedOutStream out)
            throws IOException {
        for (; i != h; i++) {
            type.writeSingleField((T) u.getObject(d[i], df), out);
        }
    }

    private void wscString(int i, final int h, final long df, final SkillObject[] d, final MappedOutStream out)
            throws IOException {
        StringType t = (StringType) type;
        for (; i != h; i++) {
            String v = (String) u.getObject(d[i], df);
            if (null == v)
                out.i8((byte) 0);
            else
                t.writeSingleField(v, out);
        }
    }

    private void wscAnnotation(int i, final int h, final long df, final SkillObject[] d, final MappedOutStream out)
            throws IOException {
        final Annotation t;
        if (type instanceof Annotation)
            t = (Annotation) type;
        else
            t = ((UnrootedInterfacePool<T>) type).getType();

        for (; i != h; i++) {
            t.writeSingleField((SkillObject) u.getObject(d[i], df), out);
        }
    }

    private void wscRef(int i, final int h, final long df, final SkillObject[] d, final MappedOutStream out)
            throws IOException {
        for (; i != h; i++) {
            SkillObject v = (SkillObject) u.getObject(d[i], df);
            if (null == v)
                out.i8((byte) 0);
            else
                out.v64(v.skillID);
        }
    }

    /**
     * Defer reading to wsc by creating adequate temporary simple chunks.
     */
    @Override
    protected final void wbc(BulkChunk c, MappedOutStream out) throws IOException {
        ArrayList<Block> blocks = owner.blocks();
        int blockIndex = 0;
        final int endBlock = c.blockCount;
        while (blockIndex < endBlock) {
            Block b = blocks.get(blockIndex++);
            wsc(new SimpleChunk(-1, -1, b.bpo, b.count), out);
        }
    }
}
