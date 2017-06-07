package de.ust.skill.common.java.internal.parts;

/**
 * A chunk that is used iff a field is appended to a preexisting type in a
 * block.
 * 
 * @author Timm Felden
 */
public class BulkChunk extends Chunk {
    
    /**
     * number of blocks represented by this chunk
     */
    public final int blockCount;

    public BulkChunk(long begin, long end, long count, int blockCount) {
        super(begin, end, count);
        this.blockCount = blockCount;
    }

}
