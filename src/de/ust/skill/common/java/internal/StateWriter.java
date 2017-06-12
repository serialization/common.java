package de.ust.skill.common.java.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Future;

import de.ust.skill.common.java.internal.parts.Chunk;
import de.ust.skill.common.java.internal.parts.SimpleChunk;
import de.ust.skill.common.jvm.streams.FileOutputStream;

final public class StateWriter extends SerializationFunctions {

    public StateWriter(SkillState state, FileOutputStream out) throws Exception {
        super(state);

        // ensure fast size() operations
        StoragePool.fixed(state.types);

        // make lbpo map, update data map to contain dynamic instances and
        // create skill IDs for serialization
        // index → bpo
        // @note pools.par would not be possible if it were an actual map:)
        final int[] lbpoMap = new int[state.types.size()];
        state.types.stream().parallel().forEach(p -> {
            if (p instanceof BasePool<?>) {
                ((BasePool<?>) p).compress(lbpoMap);
            }
        });

        /**
         * **************** PHASE 3: WRITE * ****************
         */
        // write string block
        state.strings.prepareAndWrite(out, this);

        // write count of the type block
        out.v64(state.types.size());

        // calculate offsets
        HashMap<StoragePool<?, ?>, HashMap<FieldDeclaration<?, ?>, Future<Long>>> offsets = new HashMap<>();
        for (final StoragePool<?, ?> p : state.types) {
            HashMap<FieldDeclaration<?, ?>, Future<Long>> vs = new HashMap<>();
            for (final FieldDeclaration<?, ?> f : p.dataFields)
                vs.put(f, SkillState.pool.submit(() -> f.osc((SimpleChunk) f.lastChunk())));
            offsets.put(p, vs);
        }

        // write types
        ArrayList<FieldDeclaration<?, ?>> fieldQueue = new ArrayList<>();
        for (StoragePool<?, ?> p : state.types) {
            string(p.name, out);
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
        ArrayList<Task> data = new ArrayList<>();
        long offset = 0L;
        for (FieldDeclaration<?, ?> f : fieldQueue) {
            StoragePool<?, ?> p = f.owner;
            HashMap<FieldDeclaration<?, ?>, Future<Long>> vs = offsets.get(p);

            // write info
            out.v64(f.index);
            string(f.name, out);
            writeType(f.type, out);
            restrictions(f, out);
            long end = offset + vs.get(f).get();
            out.v64(end);

            // update last chunk and prepare write
            Chunk c = f.lastChunk();
            c.begin = offset;
            c.end = end;
            data.add(new Task(f, offset, end));
            offset = end;
        }

        writeFieldData(state, out, data, (int) offset);
    }
}
