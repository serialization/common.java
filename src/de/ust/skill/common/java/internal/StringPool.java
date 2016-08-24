package de.ust.skill.common.java.internal;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import de.ust.skill.common.java.api.StringAccess;
import de.ust.skill.common.jvm.streams.FileInputStream;
import de.ust.skill.common.jvm.streams.FileOutputStream;

/**
 * @author Timm Felden
 * @note String pools use magic index 0 for faster translation of string ids to
 *       strings.
 * @note String pool may contain duplicates, if strings have been added. This is
 *       a necessary behavior, if add should be an O(1) operation and Strings
 *       are loaded from file lazily.
 */
public class StringPool implements StringAccess {
    private FileInputStream input;

    /**
     * the set of all known strings, i.e. strings which do not have an ID as
     * well as strings that already have one
     */
    private final HashSet<String> knownStrings = new HashSet<>();

    /**
     * ID ⇀ (absolute offset, length) will be used if idMap contains a null
     * reference
     *
     * @note there is a fake entry at ID 0
     */
    final ArrayList<Position> stringPositions;

    final static class Position {
        public Position(long l, int i) {
            absoluteOffset = l;
            length = i;
        }

        public long absoluteOffset;
        public int length;
    }

    /**
     * get string by ID
     */
    final ArrayList<String> idMap;

    /**
     * DO NOT CALL IF YOU ARE NOT GENERATED OR INTERNAL CODE!
     */
    public StringPool(FileInputStream input) {
        this.input = input;
        stringPositions = new ArrayList<>();
        stringPositions.add(new Position(-1L, -1));
        idMap = new ArrayList<>();
        idMap.add(null);
    }

    @Override
    public int size() {
        return knownStrings.size();
    }

    @Override
    public String get(long index) {
        if (0L == index)
            return null;

        String result;
        try {
            result = idMap.get((int) index);
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
            Position off = stringPositions.get((int) index);
            input.push(off.absoluteOffset);
            byte[] chars = input.bytes(off.length);
            input.pop();

            try {
                result = new String(chars, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                // as if that would ever happen
                e.printStackTrace();
            }
            idMap.set((int) index, result);
            knownStrings.add(result);
        }
        return result;
    }

    public void prepareAndWrite(FileOutputStream out, StateWriter ws) throws IOException {
        HashMap<String, Integer> serializationIDs = ws.stringIDs;

        ensureStrings();

        // create inverse map
        for (int i = 1; i < idMap.size(); i++) {
            serializationIDs.put(idMap.get(i), i);
        }

        // instert new strings to the map;
        // this is the place where duplications with lazy strings will be
        // detected and eliminated
        for (String s : knownStrings) {
            if (!serializationIDs.containsKey(s)) {
                serializationIDs.put(s, idMap.size());
                idMap.add(s);
            }
        }

        // count
        // @note idMap access performance hack
        out.v64(idMap.size() - 1);

        // @note idMap access performance hack
        if (1 != idMap.size()) {
            final Charset utf8 = Charset.forName("UTF-8");
            // offsets
            ByteBuffer end = ByteBuffer.allocate(4 * (idMap.size() - 1));
            int off = 0;
            for (int i = 1; i < idMap.size(); i++) {
                off += idMap.get(i).getBytes(utf8).length;
                end.putInt(off);
            }
            end.rewind();
            out.put(end);

            // data
            for (int i = 1; i < idMap.size(); i++)
                out.put(idMap.get(i).getBytes(utf8));
        }
    }

    /**
     * read remaining strings
     * 
     * @throws IOException
     */
    private final void ensureStrings() throws IOException {
        if (null != input) {
            for (int i = 1; i < stringPositions.size(); i++) {
                get(i);
            }
            input.close();
            input = null;
        }
    }

    /**
     * prepares serialization of the string pool and appends new Strings to the
     * output stream.
     */
    public void prepareAndAppend(FileOutputStream out, StateAppender as) throws IOException {
        final HashMap<String, Integer> serializationIDs = as.stringIDs;

        ensureStrings();

        // create inverse map
        for (int i = 1; i < idMap.size(); i++) {
            serializationIDs.put(idMap.get(i), i);
        }

        ArrayList<byte[]> todo = new ArrayList<byte[]>();

        // Insert new strings to the map;
        // this is the place where duplications with lazy strings will be
        // detected and eliminated
        // this is also the place, where new instances are appended to the
        // output file
        final Charset utf8 = Charset.forName("UTF-8");
        for (String s : knownStrings) {
            if (!serializationIDs.containsKey(s)) {
                serializationIDs.put(s, idMap.size());
                idMap.add(s);
                todo.add(s.getBytes(utf8));
            }
        }

        // count
        final int count = todo.size();
        out.v64(count);

        int off = 0;
        // end
        final ByteBuffer end = ByteBuffer.allocate(4 * count);
        for (byte[] s : todo) {
            off += s.length;
            end.putInt(off);
        }
        out.put(end.array());

        // data
        for (byte[] s : todo)
            out.put(s);

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
}
