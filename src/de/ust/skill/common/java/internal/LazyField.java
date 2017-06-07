package de.ust.skill.common.java.internal;

import java.nio.BufferUnderflowException;

import de.ust.skill.common.java.internal.parts.Block;
import de.ust.skill.common.java.internal.parts.Chunk;
import de.ust.skill.common.java.internal.parts.SimpleChunk;
import de.ust.skill.common.jvm.streams.MappedInStream;

/**
 * The field is distributed and loaded on demand. Unknown fields are lazy as well.
 *
 * @author Timm Felden
 * @note implementation abuses a distributed field that can be accessed iff there are no data chunks to be processed
 */
public final class LazyField<T, Obj extends SkillObject> extends DistributedField<T, Obj> {

    public LazyField(FieldType<T> type, String name, int index, StoragePool<Obj, ? super Obj> owner) {
        super(type, name, index, owner);
    }

    private boolean isLoaded = false;

    // executes pending read operations
    private void load() {
        SkillObject[] d = owner.basePool.data;
        int blockCounter = 0;

        for (de.ust.skill.common.java.internal.FieldDeclaration.ChunkEntry ce : dataChunks) {
            blockCounter++;
            Chunk chunk = ce.c;
            MappedInStream in = ce.in;
            ce.in = null;
            final long firstPosition = in.position();
            try {
                if (chunk instanceof SimpleChunk) {
                    SimpleChunk c = (SimpleChunk) chunk;
                    final int low = (int) c.bpo;
                    final int high = (int) (c.bpo + c.count);
                    for (int i = low; i < high; i++)
                        data.put(d[i], type.readSingleField(in));

                } else {
                    int count = (int) chunk.count;
                    for (Block bi : owner.blocks) {
                        count -= bi.count;
                        if (count >= 0) {
                            final int last = bi.bpo + bi.count;
                            for (int i = bi.bpo; i < last; i++) {
                                data.put(d[i], type.readSingleField(in));
                            }
                        }
                    }
                }
            } catch (BufferUnderflowException e) {
                throw new PoolSizeMissmatchError(blockCounter, chunk.begin, chunk.end, this, e);
            }
            final long lastPosition = in.position();
            if (lastPosition - firstPosition != chunk.end - chunk.begin)
                throw new PoolSizeMissmatchError(blockCounter, in.position(), chunk.begin, chunk.end, this);
        }

        isLoaded = true;
    }

    // required to ensure that data is present before state reorganization
    void ensureLoaded() {
        if (!isLoaded)
            load();
    }

    @Override
    public void read(ChunkEntry last) {
        // deferred
    }

    @Override
    public long offset() {
        if (!isLoaded)
            load();

        return super.offset();
    }

    @Override
    public T getR(SkillObject ref) {
        if (-1 == ref.skillID)
            return newData.get(ref);

        if (!isLoaded)
            load();

        return super.getR(ref);
    }

    @Override
    public void setR(SkillObject ref, T value) {
        if (-1 == ref.skillID)
            newData.put(ref, value);
        else {
            if (!isLoaded)
                load();

            super.setR(ref, value);
        }
    }
}
