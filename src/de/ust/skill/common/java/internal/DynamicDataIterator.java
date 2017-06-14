package de.ust.skill.common.java.internal;

import java.util.Iterator;

import de.ust.skill.common.java.internal.parts.Block;

/**
 * Iterates efficiently over dynamic instances of a pool.
 *
 * First phase will iterate over all blocks of the pool. The second phase will
 * iterate over all dynamic instances of the pool.
 *
 * @author Timm Felden
 */
public final class DynamicDataIterator<T extends B, B extends SkillObject> implements Iterator<T> {

    StoragePool<? extends T, B> p;

    int secondIndex;
    final int lastBlock;
    final int endHeight;
    int index;
    int last;

    public DynamicDataIterator(StoragePool<T, B> storagePool) {
        p = storagePool;
        endHeight = p.typeHierarchyHeight;
        lastBlock = p.blocks.size();
        // other fields are zero-allocated

        while (index == last && secondIndex < lastBlock) {
            Block b = p.blocks.get(secondIndex);
            index = b.bpo;
            last = index + b.count;
            secondIndex++;
        }
        // mode switch, if there is no other block
        if (index == last && secondIndex == lastBlock) {
            secondIndex++;
            while (null != p) {
                if (p.newObjects.size() != 0) {
                    index = 0;
                    last = p.newObjects.size();
                    break;
                }
                nextP();
            }
        }
    }

    @Override
    public boolean hasNext() {
        return null != p;
    }

    @Override
    public T next() {
        if (secondIndex <= lastBlock) {
            @SuppressWarnings("unchecked")
            T r = (T) p.data[index];
            index++;
            if (index == last) {
                while (index == last && secondIndex < lastBlock) {
                    Block b = p.blocks.get(secondIndex);
                    index = b.bpo;
                    last = index + b.count;
                    secondIndex++;
                }
                // mode switch, if there is no other block
                if (index == last && secondIndex == lastBlock) {
                    secondIndex++;
                    while (null != p) {
                        if (p.newObjects.size() != 0) {
                            index = 0;
                            last = p.newObjects.size();
                            break;
                        }
                        nextP();
                    }
                }
            }
            return r;
        } else {
            T r = p.newObject(index);
            index++;
            if (index == last) {
                do {
                    nextP();
                    if (null == p)
                        break;

                    if (p.newObjects.size() != 0) {
                        index = 0;
                        last = p.newObjects.size();
                        break;
                    }
                } while (true);
            }
            return r;
        }
    }

    private void nextP() {
        @SuppressWarnings("unchecked")
        final StoragePool<? extends T, B> n = (StoragePool<? extends T, B>) p.nextPool;
        if (null != n && endHeight < n.typeHierarchyHeight)
            p = n;
        else
            p = null;
    }
}
