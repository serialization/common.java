package ogss.common.java.internal;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.Semaphore;

import ogss.common.java.internal.exceptions.InvalidPoolIndexException;
import ogss.common.streams.BufferedOutStream;
import ogss.common.streams.FileInputStream;
import ogss.common.streams.FileOutputStream;
import ogss.common.streams.MappedInStream;

/**
 * @author Timm Felden
 * @note String pools use magic index 0 for faster translation of string ids to strings.
 * @note String pool may contain duplicates, if strings have been added. This is a necessary behavior, if add should be
 *       an O(1) operation and Strings are loaded from file lazily.
 */
final public class StringPool extends HullType<String> {
    public static final int typeID = 9;

    public static final Charset utf8 = Charset.forName("UTF-8");

    /**
     * keep the mapped in stream open until all strings have been read from input HS
     */
    private MappedInStream in;

    /**
     * ID ⇀ (absolute offset|32, length|32) will be used if idMap contains a null reference
     *
     * @note there is a fake entry at ID 0
     */
    long[] positions;

    /**
     * Strings used as names of types, fields or enum constants.
     * 
     * @note literals are respective to the merged type system
     */
    private String[] literals;

    StringPool(String[] literals) {
        super(typeID);
        this.literals = literals;
    }

    /**
     * Read the string literal block
     */
    void readSL(FileInputStream in) {
        final int count = in.v32();
        if (0 == count) {
            // trivial merge
            return;
        }

        // known/file literal index
        int ki = 0, fi = 0;
        String next = new String(in.bytes(-1, in.v32()), utf8);

        // merge literals from file into literals
        ArrayList<String> merged = new ArrayList<>(count);
        boolean hasFI, hasKI;
        while ((hasFI = fi < count) | (hasKI = ki < literals.length)) {
            // note: we will intern the string only if it is unknown
            final int cmp = hasFI ? (hasKI ? next.compareTo(literals[ki]) : 1) : -1;

            if (0 <= cmp) {
                if (0 == cmp) {
                    // discard next
                    next = literals[ki++];
                } else {
                    // use next
                    next = next.intern();
                }
                merged.add(next);
                idMap.add(next);

                if (++fi < count)
                    next = new String(in.bytes(-1, in.v32()), utf8);
            } else {
                merged.add(literals[ki++]);
            }
        }

        // update literals if required
        if (literals.length != merged.size()) {
            literals = merged.toArray(new String[merged.size()]);
        }
    }

    /**
     * Read HS; we will not perform an actual read afterwards
     */
    @Override
    protected int allocateInstances(int count, MappedInStream in) {
        this.in = in;

        // read offsets
        int[] offsets = new int[count];
        for (int i = 0; i < count; i++) {
            offsets[i] = in.v32();
        }

        // create positions
        int spi = idMap.size();
        final long[] sp = new long[spi + count];
        positions = sp;

        // store offsets
        // @note this has to be done after reading all offsets, as sizes are relative to that point and decoding
        // is done using absolute sizes
        int last = in.position(), len;
        for (int i = 0; i < count; i++) {
            len = offsets[i];
            sp[spi++] = (((long) last) << 32L) | len;
            idMap.add(null);
            last += len;
        }

        return 0;
    }

    @Override
    protected void read(int block, MappedInStream map) throws IOException {
        // -done- strings are lazy
    }

    /**
     * The state will ask to drop the read buffer as soon as all strings must have been loaded, i.e. as soon as all
     * other lazy field data has been loaded.
     */
    void dropRB() {
        in = null;
        positions = null;
    }

    /**
     * write the string literal block to out and release the barrier when done, so that parallel creation of T and F can
     * be written to out
     * 
     * @note the parallel write operation is synchronized on this, hence the buffer flush has to be synchronized on this
     *       as well
     */
    Semaphore writeBlock(final FileOutputStream out) {
        resetSerialization();

        // create inverse map
        for (String s : literals) {
            IDs.put(s, idMap.size());
            idMap.add(s);
        }

        Semaphore writeBarrier = new Semaphore(0, false);
        State.pool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // count
                    // @note idMap access performance hack
                    final int count = literals.length;
                    out.v64(count);
                    for (int i = 0; i < count; i++) {
                        final byte[] img = literals[i].getBytes(utf8);
                        out.v64(img.length);
                        out.put(img);
                    }
                } catch (IOException e) {
                    // should never happen!
                    e.printStackTrace();
                } finally {
                    writeBarrier.release();
                }
            }
        });
        return writeBarrier;
    }

    /**
     * Write HS
     */
    @Override
    protected final boolean write(int block, BufferedOutStream out) throws IOException {
        // the null in idMap is not written and literals are written in SL
        final int hullOffset = literals.length + 1;
        final int count = idMap.size() - hullOffset;
        if (0 == count)
            return true;

        out.v64(count);

        // note: getBytes is an expensive operation!
        final byte[][] images = new byte[count][];
        // lengths
        for (int i = 0; i < count; i++) {
            final byte[] img = idMap.get(i + hullOffset).getBytes(utf8);
            images[i] = img;
            out.v64(img.length);
        }

        // data
        for (int i = 0; i < count; i++) {
            out.put(images[i]);
        }

        return false;
    }


    @Override
    public int id(String ref) {
        return null == ref ? 0 : super.id(ref.intern());
    }

    @Override
    public String get(int index) {
        if (0L == index)
            return null;

        String result;
        // @note this block has to be synchronized in order to enable parallel
        // decoding of field data
        synchronized (this) {
            try {
                result = idMap.get(index);
            } catch (IndexOutOfBoundsException e) {
                throw new InvalidPoolIndexException(index, positions.length, "string", e);
            }
            if (null != result)
                return result;

            // we have to load the string from disk
            // @note this cannot happen if there was no HS, i.e. it is safe to access in & positions
            long off = positions[index];
            byte[] chars = in.bytes((int) (off >> 32L), (int) off);

            result = new String(chars, utf8).intern();
            idMap.set(index, result);
        }
        return result;
    }

    @Override
    public Iterator<String> iterator() {
        Iterator<String> r = idMap.iterator();
        // skip null-entry
        r.next();
        return r;
    }

    @Override
    public String name() {
        return "string";
    }
}
