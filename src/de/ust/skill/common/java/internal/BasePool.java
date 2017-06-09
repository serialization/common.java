package de.ust.skill.common.java.internal;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import de.ust.skill.common.java.internal.SkillState.ReadBarrier;
import de.ust.skill.common.java.internal.fieldDeclarations.AutoField;
import de.ust.skill.common.java.internal.parts.Block;
import de.ust.skill.common.java.internal.parts.Chunk;
import de.ust.skill.common.java.iterators.TypeOrderIterator;

/**
 * The base of a type hierarchy. Contains optimized representations of data
 * compared to sub pools.
 * 
 * @author Timm Felden
 * @param <T>
 */
public class BasePool<T extends SkillObject> extends StoragePool<T, T> {

    /**
     * workaround for fucked-up generic array types
     * 
     * @return an empty array that is used as initial value of data
     * @note has to be overridden by each concrete base pool
     */
    @SuppressWarnings({ "static-method", "unchecked" })
    protected T[] newArray(int size) {
        return (T[]) new SkillObject[size];
    }

    /**
     * the owner is set once by the SkillState.finish method!
     */
    protected SkillState owner = null;

    public BasePool(int poolIndex, String name, Set<String> knownFields, AutoField<?, T>[] autoFields) {
        super(poolIndex, name, null, knownFields, autoFields);
    }

    @Override
    public final SkillState owner() {
        return owner;
    }

    /**
     * Allocates data and all instances for this pool and all of its sub-pools.
     * 
     * @param barrier
     *            used to synchronize parallel object allocation
     * 
     * @note invoked once upon state creation before deserialization of field
     *       data
     */
    void performAllocations(final ReadBarrier barrier) {

        // allocate data and link it to sub pools
        {
            data = newArray(cachedSize);
            TypeHierarchyIterator<T, T> subs = new TypeHierarchyIterator<>(this);
            while (subs.hasNext())
                subs.next().data = data;
        }

        // allocate instances
        {
            TypeHierarchyIterator<T, T> subs = new TypeHierarchyIterator<>(this);
            while (subs.hasNext()) {
                final StoragePool<? extends T, T> s = subs.next();
                for (Block b : s.blocks) {
                    barrier.beginRead();
                    SkillState.pool.execute(() -> {
                        s.allocateInstances(b);
                        barrier.release();
                    });
                }
            }
        }
    }

    /**
     * compress new instances into the data array and update skillIDs
     */
    final void compress(int[] lbpoMap) {

        // create our part of the lbpo map
        {
            int next = 0;
            TypeHierarchyIterator<T, T> subs = new TypeHierarchyIterator<>(this);

            while (subs.hasNext()) {
                final StoragePool<? extends T, T> p = subs.next();

                final int lbpo = lbpoMap[p.typeID - 32] = next;
                next += p.staticSize() - p.deletedCount;

                for (FieldDeclaration<?, ?> f : p.dataFields)
                    f.resetChunks(lbpo, p.cachedSize);
            }
        }

        // from now on, size will take deleted objects into account, thus d may
        // in fact be smaller then data!
        T[] d = newArray(size());
        int p = 0;
        TypeOrderIterator<T, T> is = typeOrderIterator();
        while (is.hasNext()) {
            final T i = is.next();
            if (i.skillID != 0) {
                d[p++] = i;
                i.setSkillID(p);
            }
        }

        // update after compress for all sub-pools
        data = d;
        {
            TypeHierarchyIterator<T, T> subs = new TypeHierarchyIterator<>(this);
            while (subs.hasNext())
                subs.next().updateAfterCompress(lbpoMap);
        }
    }

    final void prepareAppend(Map<FieldDeclaration<?, ?>, Chunk> chunkMap) {
        boolean newInstances = newDynamicInstances().hasNext();

        // check if we have to append at all
        if (!newInstances && !blocks.isEmpty() && !dataFields.isEmpty()) {
            boolean done = true;
            for (FieldDeclaration<?, T> f : dataFields) {
                if (f.noDataChunk()) {
                    done = false;
                    break;
                }
            }
            if (done)
                return;
        }

        if (newInstances) {
            // we have to resize
            final T[] d = Arrays.copyOf(data, size());
            int i = data.length;

            final Iterator<T> is = newDynamicInstances();
            while (is.hasNext()) {
                final T instance = is.next();
                d[i++] = instance;
                instance.setSkillID(i);
            }
            data = d;
        }

        TypeHierarchyIterator<T, T> ts = new TypeHierarchyIterator<>(this);
        while (ts.hasNext())
            ts.next().updateAfterPrepareAppend(chunkMap);
    }
}
