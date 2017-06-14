package de.ust.skill.common.java.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;

import de.ust.skill.common.java.internal.parts.BulkChunk;
import de.ust.skill.common.java.internal.parts.Chunk;
import de.ust.skill.common.java.internal.parts.SimpleChunk;
import de.ust.skill.common.jvm.streams.FileOutputStream;

/**
 * Implementation of append operation.
 * 
 * @author Timm Felden
 */
final public class StateAppender extends SerializationFunctions {

    public StateAppender(SkillState state, FileOutputStream out)
            throws IOException, InterruptedException, ExecutionException {
        super(state);

        // save the index of the first new pool
        final int newPoolIndex;
        {
            int i = 0;
            for (StoragePool<?, ?> t : state.types) {
                if (t.blocks.isEmpty())
                    break;
                i++;
            }
            newPoolIndex = i;
        }

        // ensure fast size() operations
        StoragePool.fixed(state.types);

        // make lbpsi map, update data map to contain dynamic instances and
        // create serialization skill IDs for
        // serialization
        // index → bpsi
        final int[] lbpoMap = new int[state.types.size()];
        final HashMap<FieldDeclaration<?, ?>, Chunk> chunkMap = new HashMap<>();
        final Semaphore barrier = new Semaphore(0, false);
        {
            int bases = 0;
            for (StoragePool<?, ?> p : state.types) {
                if (p instanceof BasePool<?>) {
                    bases++;
                    SkillState.pool.execute(new Runnable() {
                        @Override
                        public void run() {
                            ((BasePool<?>) p).prepareAppend(lbpoMap, chunkMap);
                            barrier.release();
                        }
                    });
                }
            }
            barrier.acquire(bases);
        }

        // locate relevant pools
        final ArrayList<StoragePool<?, ?>> rPools = new ArrayList<>(state.types.size());
        for (StoragePool<?, ?> p : state.types) {
            // new index?
            if (p.typeID - 32 >= newPoolIndex)
                rPools.add(p);
            // new instance or field?
            else if (p.size() > 0) {
                boolean exists = false;
                for (FieldDeclaration<?, ?> f : p.dataFields) {
                    if (chunkMap.containsKey(f)) {
                        exists = true;
                        break;
                    }
                }
                if (exists)
                    rPools.add(p);
            }
        }

        /**
         * **************** PHASE 3: WRITE * ****************
         */

        // write string block
        state.strings.prepareAndAppend(out, this);

        // calculate offsets for relevant fields
        int fieldCount = 0;
        {
            for (final FieldDeclaration<?, ?> f : chunkMap.keySet()) {
                fieldCount++;
                SkillState.pool.execute(new Runnable() {
                    public void run() {
                        f.offset = 0;
                        Chunk c = f.lastChunk();
                        if (c instanceof SimpleChunk) {
                            int i = (int) ((SimpleChunk) c).bpo;
                            f.osc(i, i + (int) c.count);
                        } else
                            f.obc((BulkChunk) c);
                        barrier.release();
                    };
                });
            }
            barrier.acquire(fieldCount);
        }

        // write count of the type block
        out.v64(rPools.size());

        // write headers
        final ArrayList<ArrayList<FieldDeclaration<?, ?>>> fieldQueue = new ArrayList<>(fieldCount);
        for (StoragePool<?, ?> p : rPools) {
            // generic append
            final boolean newPool = p.typeID - 32 >= newPoolIndex;
            final ArrayList<FieldDeclaration<?, ?>> fields = new ArrayList<FieldDeclaration<?, ?>>(p.dataFields.size());
            for (FieldDeclaration<?, ?> f : p.dataFields)
                if (chunkMap.containsKey(f))
                    fields.add(f);

            if (newPool || (0 != fields.size() && p.size() > 0)) {

                out.v64(stringIDs.get(p.name));
                final long count = p.lastBlock().count;
                out.v64(count);

                if (newPool) {
                    restrictions(p, out);
                    if (null == p.superName()) {
                        out.i8((byte) 0);
                    } else {
                        out.v64(p.superPool.typeID - 31);
                        if (0 != count) {
                            // we used absolute BPOs to ease overall
                            // implementation
                            out.v64(lbpoMap[p.typeID - 32] - lbpoMap[p.basePool.typeID - 32]);
                        }

                    }
                } else if (null != p.superName() && 0 != count) {
                    out.v64(lbpoMap[p.typeID - 32] - lbpoMap[p.basePool.typeID - 32]);
                }

                if (newPool && 0 == count) {
                    out.i8((byte) 0);
                } else {
                    out.v64(fields.size());
                    fieldQueue.add(fields);
                }
            }
        }

        // write fields
        final ArrayList<Task> data = new ArrayList<>(fieldCount);
        long offset = 0L;
        for (ArrayList<FieldDeclaration<?, ?>> fields : fieldQueue) {
            for (FieldDeclaration<?, ?> f : fields) {
                out.v64(f.index);

                if (1 == f.dataChunks.size()) {
                    out.v64(stringIDs.get(f.name));
                    writeType(f.type, out);
                    restrictions(f, out);
                }

                // put end offset and enqueue data
                final long end = offset + f.offset;
                out.v64(end);
                data.add(new Task(f, offset, end));
                offset = end;
            }
        }

        writeFieldData(state, out, data, (int) offset, barrier);
    }
}
