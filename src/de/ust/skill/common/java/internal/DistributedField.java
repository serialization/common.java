package de.ust.skill.common.java.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import de.ust.skill.common.java.internal.parts.Block;
import de.ust.skill.common.java.internal.parts.BulkChunk;
import de.ust.skill.common.java.restrictions.FieldRestriction;
import de.ust.skill.common.jvm.streams.MappedInStream;
import de.ust.skill.common.jvm.streams.MappedOutStream;

/**
 * The fields data is distributed into an array (for now its a hash map) holding
 * its instances.
 */
public class DistributedField<T, Obj extends SkillObject> extends FieldDeclaration<T, Obj> {

    public DistributedField(FieldType<T> type, String name, StoragePool<Obj, ? super Obj> owner) {
        super(type, name, owner);
    }

    // data held as in storage pools
    // @note C++-style implementation is not possible on JVM
    protected HashMap<SkillObject, T> data = new HashMap<>();
    protected HashMap<SkillObject, T> newData = new HashMap<>();

    /**
     * Check consistency of restrictions on this field.
     */
    @Override
    void check() {
        for (FieldRestriction<T> r : restrictions) {
            for (T x : data.values())
                r.check(x);
            for (T x : newData.values())
                r.check(x);
        }
    }

    @Override
    protected void rsc(int i, final int h, MappedInStream in) {
        final SkillObject[] d = owner.basePool.data;
        for (; i != h; i++) {
            data.put(d[i], type.readSingleField(in));
        }
    }

    @Override
    protected void rbc(BulkChunk c, MappedInStream in) {
        final SkillObject[] d = owner.basePool.data;
        ArrayList<Block> blocks = owner.blocks();
        int blockIndex = 0;
        final int endBlock = c.blockCount;
        while (blockIndex < endBlock) {
            Block b = blocks.get(blockIndex++);
            int i = b.bpo;
            for (final int h = i + b.count; i != h; i++) {
                data.put(d[i], type.readSingleField(in));
            }
        }
    }

    // TODO distributed fields need to be compressed as well!

    protected final long offset() {
        final Block range = owner.lastBlock();
        // @note order is not important, because we calculate offsets only!!!
        if (range.count == data.size())
            return type.calculateOffset(data.values());

        // we have to filter the right values
        long rval = 0;
        for (HashMap.Entry<SkillObject, T> e : data.entrySet())
            if (range.contains(e.getKey().skillID))
                rval += type.singleOffset(e.getValue());
        return rval;
    }

    @Override
    protected void osc(int i, int h) {
        offset = offset();
    }

    @Override
    protected void obc(BulkChunk c) {
        offset = offset();
    }

    @Override
    protected void wsc(int i, final int h, MappedOutStream out) throws IOException {
        final SkillObject[] d = owner.basePool.data;
        for (; i < h; i++) {
            type.writeSingleField(data.get(d[i]), out);
        }
    }

    @Override
    protected void wbc(BulkChunk c, MappedOutStream out) throws IOException {
        final SkillObject[] d = owner.basePool.data;
        for (Block bi : owner.blocks) {
            int i = bi.bpo;
            for (final int end = i + bi.count; i < end; i++) {
                type.writeSingleField(data.get(d[i]), out);
            }
        }
    }

    @Override
    public T get(SkillObject ref) {
        if (-1 == ref.skillID)
            return newData.get(ref);

        return data.get(ref);
    }

    @Override
    public void set(SkillObject ref, T value) {
        if (-1 == ref.skillID)
            newData.put(ref, value);
        else
            data.put(ref, value);

    }

}
