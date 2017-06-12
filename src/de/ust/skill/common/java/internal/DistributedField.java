package de.ust.skill.common.java.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

import de.ust.skill.common.java.internal.parts.Block;
import de.ust.skill.common.java.internal.parts.BulkChunk;
import de.ust.skill.common.java.internal.parts.SimpleChunk;
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
    // @note see paper notes for O(1) implementation
    protected HashMap<SkillObject, T> data = new HashMap<>(); // Array[T]()
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
    protected void rsc(SimpleChunk c, MappedInStream in) {
        final SkillObject[] d = owner.basePool.data;
        int i = (int) c.bpo;
        for (final int h = i + (int) c.count; i != h; i++) {
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

    @SuppressWarnings("unchecked")
    protected final long offset() {
        final Block range = owner.lastBlock();
        // @note order is not important, because we calculate offsets only!!!
        if (range.count == data.size())
            return type.calculateOffset(data.values());

        // we have to filter the right values
        return type.calculateOffset((Collection<T>) Arrays.asList(data.entrySet().stream()
                .filter(e -> range.contains(e.getKey().skillID)).map(e -> e.getValue()).toArray()));
    }


    @Override
    protected long osc(SimpleChunk c) {
        return offset();
    }

    @Override
    protected long obc(BulkChunk c) {
        return offset();
    }

    @Override
    protected void wsc(SimpleChunk c, MappedOutStream out) throws IOException {
        final SkillObject[] d = owner.basePool.data;
        int low = (int) c.bpo;
        int high = (int) (c.bpo + c.count);
        for (int i = low; i < high; i++) {
            type.writeSingleField(data.get(d[i]), out);
        }
    }

    @Override
    protected void wbc(BulkChunk c, MappedOutStream out) throws IOException {
        final SkillObject[] d = owner.basePool.data;
        for (Block bi : owner.blocks) {
            final int end = bi.bpo + bi.count;
            for (int i = bi.bpo; i < end; i++) {
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
