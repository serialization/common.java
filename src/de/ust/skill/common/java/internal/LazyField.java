package de.ust.skill.common.java.internal;

import java.util.HashMap;
import java.util.Map.Entry;

import de.ust.skill.common.java.internal.parts.BulkChunk;
import de.ust.skill.common.java.internal.parts.Chunk;
import de.ust.skill.common.java.internal.parts.SimpleChunk;
import de.ust.skill.common.jvm.streams.MappedInStream;

/**
 * The field is distributed and loaded on demand. Unknown fields are lazy as
 * well.
 *
 * @author Timm Felden
 * @note implementation abuses a distributed field that can be accessed iff
 *       there are no data chunks to be processed
 */
public final class LazyField<T, Obj extends SkillObject> extends DistributedField<T, Obj> {

    public LazyField(FieldType<T> type, String name, int index, StoragePool<Obj, ? super Obj> owner) {
        super(type, name, index, owner);
    }

    // is loaded <-> chunkMap == null
    private HashMap<Chunk, MappedInStream> chunkMap = new HashMap<>();

    // executes pending read operations
    private void load() {
        for (Entry<Chunk, MappedInStream> p : chunkMap.entrySet()) {
            if (p.getKey().count > 0) {
                if (p.getKey() instanceof BulkChunk)
                    super.rbc((BulkChunk) p.getKey(), p.getValue());
                else
                    super.rsc((SimpleChunk) p.getKey(), p.getValue());
            }
        }

        chunkMap = null;
    }

    // required to ensure that data is present before state reorganization
    void ensureLoaded() {
        if (null != chunkMap)
            load();
    }

    @Override
    void check() {
        // check only, if is loaded
        if (null == chunkMap)
            super.check();
    }

    @Override
    void resetChunks(int lbpo, int newSize) {
        if (null != chunkMap)
            throw new Error();
        super.resetChunks(lbpo, newSize);
    }

    @Override
    protected final void rsc(SimpleChunk target, MappedInStream in) {
        synchronized (this) {
            chunkMap.put(target, in);
        }
    }

    @Override
    protected final void rbc(BulkChunk target, MappedInStream in) {
        synchronized (this) {
            chunkMap.put(target, in);
        }
    }

    @Override
    public long offset() {
        if (null != chunkMap)
            load();

        return super.offset();
    }

    @Override
    public T get(SkillObject ref) {
        if (-1 == ref.skillID)
            return newData.get(ref);

        if (null != chunkMap)
            load();

        return super.get(ref);
    }

    @Override
    public void set(SkillObject ref, T value) {
        if (-1 == ref.skillID)
            newData.put(ref, value);
        else {
            if (null != chunkMap)
                load();

            super.set(ref, value);
        }
    }
}
