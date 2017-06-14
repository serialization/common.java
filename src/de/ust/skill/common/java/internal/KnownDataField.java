package de.ust.skill.common.java.internal;

import java.io.IOException;
import java.util.ArrayList;

import de.ust.skill.common.java.internal.fieldDeclarations.KnownField;
import de.ust.skill.common.java.internal.parts.Block;
import de.ust.skill.common.java.internal.parts.BulkChunk;
import de.ust.skill.common.jvm.streams.MappedInStream;
import de.ust.skill.common.jvm.streams.MappedOutStream;

public abstract class KnownDataField<T, Obj extends SkillObject> extends FieldDeclaration<T, Obj>
        implements KnownField<T, Obj> {

    protected KnownDataField(FieldType<T> type, String name, StoragePool<Obj, ? super Obj> owner) {
        super(type, name, owner);
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
            int i = b.bpo;
            rsc(i, i + b.count, in);
        }
    }

    /**
     * Defer reading to osc by creating adequate temporary simple chunks.
     */
    @Override
    protected final void obc(BulkChunk c) {
        ArrayList<Block> blocks = owner.blocks();
        int blockIndex = 0;
        final int endBlock = c.blockCount;
        while (blockIndex < endBlock) {
            Block b = blocks.get(blockIndex++);
            int i = b.bpo;
            osc(i, i + b.count);
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
            int i = b.bpo;
            wsc(i, i + b.count, out);
        }
    }
}
