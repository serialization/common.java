package de.ust.skill.common.java.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

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
        final Map<FieldDeclaration<?, ?>, Chunk> chunkMap = Collections.synchronizedMap(new HashMap<>());
        state.types.stream().parallel().forEach(p -> {
            if (p instanceof BasePool<?>) {
                ((BasePool<?>) p).prepareAppend(lbpoMap, chunkMap);
            }
        });

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
        final HashMap<StoragePool<?, ?>, HashMap<FieldDeclaration<?, ?>, Future<Long>>> offsets = new HashMap<>();
        for (StoragePool<?, ?> p : rPools)
            offsets.put(p, new HashMap<>());

        for (final FieldDeclaration<?, ?> f : chunkMap.keySet()) {
            final FutureTask<Long> v = new FutureTask<>(() -> {
                Chunk c = f.lastChunk();
                if (c instanceof BulkChunk)
                    return f.obc((BulkChunk) c);
                else
                    return f.osc((SimpleChunk) c);
            });
            SkillState.pool.execute(v);
            offsets.get(f.owner).put(f, v);
        }

        // write count of the type block
        out.v64(rPools.size());

        // write headers
        final ArrayList<ArrayList<FieldDeclaration<?, ?>>> fieldQueue = new ArrayList<>();
        for (StoragePool<?, ?> p : rPools) {
            // generic append
            final boolean newPool = p.typeID - 32 >= newPoolIndex;
            final ArrayList<FieldDeclaration<?, ?>> fields = new ArrayList<FieldDeclaration<?, ?>>(p.dataFields.size());
            for (FieldDeclaration<?, ?> f : p.dataFields)
                if (chunkMap.containsKey(f))
                    fields.add(f);

            if (newPool || (0 != fields.size() && p.size() > 0)) {

                string(p.name, out);
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
        final ArrayList<Task> data = new ArrayList<>();
        long offset = 0L;
        for (ArrayList<FieldDeclaration<?, ?>> fields : fieldQueue) {
            for (FieldDeclaration<?, ?> f : fields) {
                final StoragePool<?, ?> p = f.owner;
                final HashMap<FieldDeclaration<?, ?>, Future<Long>> vs = offsets.get(p);
                out.v64(f.index);

                if (f.lastChunk() instanceof BulkChunk) {
                    string(f.name, out);
                    writeType(f.type, out);
                    restrictions(f, out);
                }

                // put end offset and enqueue data
                final long end = offset + vs.get(f).get();
                out.v64(end);
                data.add(new Task(f, offset, end));
                offset = end;
            }
        }

        writeFieldData(state, out, data, (int) offset);
    }
}
