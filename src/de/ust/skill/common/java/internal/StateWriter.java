package de.ust.skill.common.java.internal;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import de.ust.skill.common.java.internal.parts.Chunk;
import de.ust.skill.common.java.internal.parts.SimpleChunk;
import de.ust.skill.common.jvm.streams.FileOutputStream;

final public class StateWriter extends SerializationFunctions {
    private static final class OT implements Runnable {
        private final FieldDeclaration<?, ?> f;
        private final Semaphore barrier;

        OT(FieldDeclaration<?, ?> f, Semaphore barrier) {
            this.f = f;
            this.barrier = barrier;
        }

        @Override
        public void run() {
            f.offset = 0;
            SimpleChunk c = (SimpleChunk) f.lastChunk();
            int i = (int) c.bpo;
            f.osc(i, i + (int) c.count);
            barrier.release();
        };

    }

    public StateWriter(SkillState state, FileOutputStream out) throws Exception {
        super(state);

        // ensure fast size() operations
        StoragePool.fixed(state.types);

        // make lbpo map, update data map to contain dynamic instances and
        // create skill IDs for serialization
        // index → bpo
        // @note pools.par would not be possible if it were an actual map:)
        final int[] lbpoMap = new int[state.types.size()];
        final Semaphore barrier = new Semaphore(0, false);
        {
            int bases = 0;
            for (StoragePool<?, ?> p : state.types) {
                if (p instanceof BasePool<?>) {
                    bases++;
                    SkillState.pool.execute(new Runnable() {
                        @Override
                        public void run() {
                            ((BasePool<?>) p).compress(lbpoMap);
                            barrier.release();
                        }
                    });
                }
            }
            barrier.acquire(bases);
        }

        /**
         * **************** PHASE 3: WRITE * ****************
         */
        // write string block
        state.strings.prepareAndWrite(out, this);

        // write count of the type block
        out.v64(state.types.size());

        // calculate offsets
        int fieldCount = 0;
        {
            for (final StoragePool<?, ?> p : state.types) {
                for (FieldDeclaration<?, ?> f : p.dataFields) {
                    fieldCount++;
                    SkillState.pool.execute(new OT(f, barrier));
                }
            }
            barrier.acquire(fieldCount);
        }

        // write types
        ArrayList<FieldDeclaration<?, ?>> fieldQueue = new ArrayList<>(fieldCount);
        for (StoragePool<?, ?> p : state.types) {
            out.v64(stringIDs.get(p.name));
            long LCount = p.lastBlock().count;
            out.v64(LCount);
            restrictions(p, out);
            if (null == p.superPool)
                out.i8((byte) 0);
            else {
                out.v64(p.superPool.typeID - 31);
                if (0L != LCount)
                    out.v64(lbpoMap[p.typeID - 32]);
            }

            out.v64(p.dataFields.size());
            fieldQueue.addAll(p.dataFields);
        }

        // write fields
        ArrayList<Task> data = new ArrayList<>(fieldCount);
        long offset = 0L;
        for (FieldDeclaration<?, ?> f : fieldQueue) {

            // write info
            out.v64(f.index);
            out.v64(stringIDs.get(f.name));
            writeType(f.type, out);
            restrictions(f, out);
            long end = offset + f.offset;
            out.v64(end);

            // update last chunk and prepare write
            Chunk c = f.lastChunk();
            c.begin = offset;
            c.end = end;
            data.add(new Task(f, offset, end));
            offset = end;
        }

        writeFieldData(state, out, data, (int) offset, barrier);
    }
}
