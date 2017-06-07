package de.ust.skill.common.java.internal;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;

import de.ust.skill.common.java.api.GeneralAccess;
import de.ust.skill.common.java.api.SkillException;
import de.ust.skill.common.java.internal.SkillState.ReadBarrier;
import de.ust.skill.common.java.internal.fieldDeclarations.IgnoredField;
import de.ust.skill.common.java.internal.parts.BulkChunk;
import de.ust.skill.common.java.internal.parts.Chunk;
import de.ust.skill.common.java.restrictions.FieldRestriction;
import de.ust.skill.common.jvm.streams.FileInputStream;
import de.ust.skill.common.jvm.streams.MappedInStream;
import de.ust.skill.common.jvm.streams.MappedOutStream;

/**
 * Actual implementation as used by all bindings.
 * 
 * @author Timm Felden
 */
abstract public class FieldDeclaration<T, Obj extends SkillObject>
        implements de.ust.skill.common.java.api.FieldDeclaration<T> {

    /**
     * @note types may change during file parsing. this may seem like a hack,
     *       but it makes file parser implementation a lot easier, because there
     *       is no need for two mostly similar type hierarchy implementations
     */
    protected FieldType<T> type;

    @Override
    public final FieldType<T> type() {
        return type;
    }

    /**
     * skill name of this
     */
    final String name;

    @Override
    public String name() {
        return name;
    }

    /**
     * index as used in the file
     * 
     * @note index is > 0, if the field is an actual data field
     * @note index = 0, if the field is SKilLID
     * @note index is <= 0, if the field is an auto field (or SKilLID)
     */
    final int index;

    /**
     * the enclosing storage pool
     */
    protected final StoragePool<Obj, ? super Obj> owner;

    /**
     * Restriction handling.
     */
    public final HashSet<FieldRestriction<T>> restrictions = new HashSet<>();

    @SuppressWarnings("unchecked")
    public <U> void addRestriction(FieldRestriction<U> r) {
        restrictions.add((FieldRestriction<T>) r);
    }

    /**
     * Check consistency of restrictions on this field.
     */
    void check() {
        if (!restrictions.isEmpty())
            for (Obj x : owner)
                if (!x.isDeleted())
                    for (FieldRestriction<T> r : restrictions)
                        r.check(x.get(this));
    }

    @Override
    public GeneralAccess<Obj> owner() {
        return owner;
    }

    protected FieldDeclaration(FieldType<T> type, String name, int index, StoragePool<Obj, ? super Obj> owner) {
        this.type = type;
        this.name = name.intern(); // we will switch on names, thus we need to
                                   // intern them
        this.index = index;
        this.owner = owner;
    }

    @Override
    public String toString() {
        return type.toString() + " " + name;
    }

    /**
     * Field declarations are equal, iff their names and types are equal.
     * 
     * @note This makes fields of unequal enclosing types equal!
     */
    @Override
    public final boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj instanceof FieldDeclaration) {
            return ((FieldDeclaration<?, ?>) obj).name().equals(name)
                    && ((FieldDeclaration<?, ?>) obj).type.equals(type);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return type.hashCode() ^ name.hashCode();
    }

    /**
     * Data chunk information, as it is required for parsing of field data.
     */
    protected static class ChunkEntry {
        public final Chunk c;
        public MappedInStream in;

        ChunkEntry(Chunk c) {
            this.c = c;
        }
    }

    protected final LinkedList<ChunkEntry> dataChunks = new LinkedList<>();

    public final void addChunk(Chunk chunk) {
        dataChunks.add(new ChunkEntry(chunk));
    }

    /**
     * Fix offset and create memory map for field data parsing.
     * 
     * @return the end of this chunk
     */
    final long addOffsetToLastChunk(FileInputStream in, long offset) {
        Chunk c = dataChunks.getLast().c;
        c.begin += offset;
        c.end += offset;

        dataChunks.getLast().in = in.map(0L, c.begin, c.end);

        return c.end;
    }

    final boolean noDataChunk() {
        return dataChunks.isEmpty();
    }

    final Chunk lastChunk() {
        return dataChunks.getLast().c;
    }

    /**
     * reset Chunks before writing a file
     */
    void resetChunks(int lbpo, int newSize) {
        dataChunks.clear();
        dataChunks.add(new ChunkEntry(new BulkChunk(-1, -1, newSize, 1)));
    }

    /**
     * Read data from a mapped input stream and set it accordingly. This is
     * invoked at the very end of state construction and done massively in
     * parallel.
     */
    protected abstract void read(ChunkEntry target);

    /**
     * offset calculation as preparation of writing data belonging to the owners
     * last block
     */
    public abstract long offset();

    /**
     * write data into a map at the end of a write/append operation
     * 
     * @note this will always write the last chunk, as, in contrast to read, it
     *       is impossible to write to fields in parallel
     * @note only called, if there actually is field data to be written
     */
    public abstract void write(MappedOutStream out) throws SkillException, IOException;

    /**
     * Coordinates reads and prevents from state corruption using the barrier.
     * 
     * @param barrier
     *            takes one permit in the caller thread and returns one in the
     *            reader thread (per block)
     * @param readErrors
     *            errors will be reported in this queue
     */
    final void finish(ReadBarrier barrier, final ConcurrentLinkedQueue<SkillException> readErrors) {
        // skip lazy and ignored fields
        if ((this instanceof LazyField<?, ?> || this instanceof IgnoredField))
            return;

        int block = 0;
        for (ChunkEntry chunk : dataChunks) {
            barrier.beginRead();
            final int blockCounter = block++;
            final FieldDeclaration<T, Obj> f = this;
            final ChunkEntry ce = chunk;

            SkillState.pool.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        f.read(ce);
                        // check that map was fully consumed and remove it
                        MappedInStream map = ce.in;
                        ce.in = null;
                        if (!map.eof())
                            readErrors.add(
                                    new PoolSizeMissmatchError(blockCounter, map.position(), ce.c.begin, ce.c.end, f));

                    } catch (BufferUnderflowException e) {
                        readErrors.add(new PoolSizeMissmatchError(blockCounter, ce.c.begin, ce.c.end, f, e));
                    } catch (SkillException t) {
                        readErrors.add(t);
                    } catch (Throwable t) {
                        t.printStackTrace();
                    } finally {
                        barrier.release(1);
                    }
                }
            });
        }
    }

    /**
     * punch a hole into the java type system :)
     */
    @SuppressWarnings("unchecked")
    protected static final <T, U> FieldType<T> cast(FieldType<U> f) {
        return (FieldType<T>) f;
    }

    /**
     * punch a hole into the java type system that eases implementation of maps
     * of interfaces
     * 
     * @note the hole is only necessary, because interfaces cannot inherit from
     *       classes
     */
    @SuppressWarnings("unchecked")
    protected static final <K1, V1, K2, V2> HashMap<K1, V1> castMap(HashMap<K2, V2> arg) {
        return (HashMap<K1, V1>) arg;
    }
}
