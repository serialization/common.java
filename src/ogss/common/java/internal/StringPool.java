package ogss.common.java.internal;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.Semaphore;

import ogss.common.java.api.StringAccess;
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
final public class StringPool extends HullType<String> implements StringAccess {
    public static final int typeID = 9;

    public static final Charset utf8 = Charset.forName("UTF-8");

    private FileInputStream input;

    /**
     * the set of all known strings, i.e. strings which do not have an ID as well as strings that already have one
     */
    final HashSet<String> knownStrings = new HashSet<>();

    /**
     * ID ⇀ (absolute offset, length) will be used if idMap contains a null reference
     *
     * @note there is a fake entry at ID 0 TODO replace by long[]; add a second array for hullStrings
     */
    final ArrayList<Position> stringPositions;

    // TODO replace by a single long
    final static class Position {
        public Position(int l, int i) {
            absoluteOffset = l;
            length = i;
        }

        public int absoluteOffset;
        public int length;
    }

    StringPool(FileInputStream input) {
        super(typeID);
        this.input = input;
        stringPositions = new ArrayList<>();
        stringPositions.add(new Position(-1, -1));
    }

    /**
     * write the string block to out and release the barrier when done, so that parallel creation of T and F can be
     * written to out
     * 
     * @note the parallel write operation is synchronized on this, hence the buffer flush has to be synchronized on this
     *       as well
     */
    Semaphore writeBlock(final FileOutputStream out) {
        resetSerialization();

        // create inverse map
        for (String s : knownStrings) {
            if (!IDs.containsKey(s)) {
                IDs.put(s, idMap.size());
                idMap.add(s);
            }
        }

        Semaphore writeBarrier = new Semaphore(0, false);
        State.pool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // count
                    // @note idMap access performance hack
                    hullOffset = idMap.size();
                    final int count = hullOffset - 1;
                    out.v64(count);

                    // @note idMap access performance hack
                    if (0 != count) {

                        // note: getBytes is an expensive operation!
                        final byte[][] images = new byte[count][];
                        // lengths
                        for (int i = 0; i < count; i++) {
                            final byte[] img = idMap.get(i + 1).getBytes(utf8);
                            images[i] = img;
                            out.v64(img.length);
                        }

                        // data
                        for (int i = 0; i < count; i++) {
                            out.put(images[i]);
                        }
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

    int hullOffset;

    @Override
    protected final boolean write(BufferedOutStream out) throws IOException {
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

        // cleanup
        IDs.clear();
        return false;
    }

    @Override
    public int size() {
        return knownStrings.size();
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
        try {
            result = idMap.get(index);
        } catch (IndexOutOfBoundsException e) {
            throw new InvalidPoolIndexException(index, stringPositions.size(), "string", e);
        }
        if (null != result)
            return result;

        // we have to load the string from disk
        // @note this block has to be synchronized in order to enable parallel
        // decoding of field data
        // @note this is correct, because string pool is the only one who can do
        // parallel operations on input!
        synchronized (this) {
            Position off = stringPositions.get(index);
            byte[] chars = input.bytes(off.absoluteOffset, off.length);

            result = new String(chars, utf8);
            idMap.set(index, result);
            knownStrings.add(result);
        }
        return result;
    }

    /**
     * Sequential read as invoked from the file parser for type and field names. The strings will be interned to ensure
     * that they are identical to generated type names.
     */
    String read(FileInputStream in) {
        final int index = in.v32();
        if (0 == index)
            return null;

        String result;
        try {
            result = idMap.get(index);
        } catch (IndexOutOfBoundsException e) {
            throw new InvalidPoolIndexException(index, stringPositions.size(), "string", e);
        }
        if (null != result)
            return result;

        // load the string from disk
        Position off = stringPositions.get(index);
        byte[] chars = input.bytes(off.absoluteOffset, off.length);

        result = new String(chars, utf8).intern();
        idMap.set(index, result);
        knownStrings.add(result);

        return result;
    }

    @Override
    protected void allocateInstances(int count, MappedInStream in) {
        // read offsets
        int last = 0;
        int[] offsets = new int[count];
        for (int i = 0; i < count; i++) {
            last += in.v32();
            offsets[i] = last;
        }

        // store offsets
        // @note this has to be done after reading all offsets, as sizes are relative to that point and decoding
        // is done using absolute sizes
        last = 0;
        for (int i = 0; i < count; i++) {
            stringPositions.add(new StringPool.Position(in.position() + last, offsets[i] - last));
            idMap.add(null);
            last = offsets[i];
        }
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean contains(Object o) {
        return knownStrings.contains(o);
    }

    @Override
    public Iterator<String> iterator() {
        return knownStrings.iterator();
    }

    @Override
    public Object[] toArray() {
        return knownStrings.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return knownStrings.toArray(a);
    }

    @Override
    public boolean add(String e) {
        if (e != null)
            return knownStrings.add(e);
        return false;
    }

    @Override
    public boolean remove(Object o) {
        return knownStrings.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return knownStrings.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends String> c) {
        return knownStrings.addAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return knownStrings.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return knownStrings.retainAll(c);
    }

    @Override
    public void clear() {
        knownStrings.clear();
    }

    boolean hasInStream() {
        return null != input;
    }

    FileInputStream getInStream() {
        return input;
    }

    @Override
    public String name() {
        return "string";
    }

    @Override
    protected void read() throws IOException {
        throw new NoSuchMethodError();
    }
}
