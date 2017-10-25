package de.ust.skill.common.java.internal;

import java.util.Iterator;

import de.ust.skill.common.java.internal.parts.Block;

/**
 * Iterates efficiently over static instances of a pool.
 *
 * @author Timm Felden
 */
public class StaticDataIterator<T extends SkillObject> implements Iterator<T> {

    // ! target pool
    final StoragePool<T, ? super T> p;

    int secondIndex;
    final int lastBlock;
    int index;
    int last;

    public StaticDataIterator(StoragePool<T, ? super T> storagePool) {
        p = storagePool;
        lastBlock = storagePool.blocks.size();
        // @note other members are zero-allocated

        // find first valid position
        while (index == last && secondIndex < lastBlock) {
            Block b = p.blocks.get(secondIndex);
            index = b.bpo;
            last = index + b.staticCount;
            secondIndex++;
        }
        // mode switch, if there is no other block
        if (index == last && secondIndex == lastBlock) {
            secondIndex++;
            index = 0;
            last = p.newObjects.size();
        }
    }

    @Override
    public boolean hasNext() {
        return index != last;
    }

    @Override
    public T next() {
        if (secondIndex <= lastBlock) {
            @SuppressWarnings("unchecked")
            T r = (T) p.data[index];
            index++;
            if (index == last) {
                while (index == last && secondIndex < lastBlock) {
                    Block b = p.blocks().get(secondIndex);
                    index = b.bpo;
                    last = index + b.staticCount;
                    secondIndex++;
                }
                // mode switch, if there is no other block
                if (index == last && secondIndex == lastBlock) {
                    secondIndex++;
                    index = 0;
                    last = p.newObjects.size();
                }
            }
            return r;
        }

        T r = p.newObject(index);
        index++;
        return r;
    }

}
