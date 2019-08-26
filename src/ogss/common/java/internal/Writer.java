package ogss.common.java.internal;

import ogss.common.java.api.OGSSException;
import ogss.common.java.internal.fieldTypes.ArrayType;
import ogss.common.java.internal.fieldTypes.ListType;
import ogss.common.java.internal.fieldTypes.MapType;
import ogss.common.java.internal.fieldTypes.SetType;
import ogss.common.streams.BufferedOutStream;
import ogss.common.streams.FileOutputStream;
import ogss.common.streams.OutStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

final public class Writer {

    /**
     * The outgoing file type ids.
     * There is one entry per TID in the runtime type system.
     *
     * @note this table is required, because we can drop types and we want the runtime TIDs to be immutable unique and ordered
     */
    private final int[] FTID;
    /**
     * This FTID is assigned to a type when it introduces a new TID in the outgoing type system
     */
    private int nextFTID = 10;

    /**
     * The outgoing file field IDs.
     */
    final int[] FFID;

    private final State state;

    /**
     * TODO serialization of restrictions
     */
    private static final void restrictions(Pool<?> p, OutStream out) throws IOException {
        out.i8((byte) 0);
    }

    /**
     * TODO serialization of restrictions
     */
    private static final void restrictions(FieldDeclaration<?, ?> f, OutStream out) throws IOException {
        out.i8((byte) 0);
    }

    // async reads will post their errors in this queue
    Throwable writeErrors = null;

    // our job synchronization barrier
    final Semaphore barrier = new Semaphore(0, false);

    /**
     * the number of buffers that will be sent to the write job; synchronize on this to protect it on modification
     */
    int awaitBuffers = 0;

    // @note can be used to add buffers concurrently to the write queue
    // @note the permit is given after we added a buffer; therefore the reader
    // can always read some buffer if he uses our permit (order is not
    // important)
    final ConcurrentLinkedQueue<BufferedOutStream> finishedBuffers = new ConcurrentLinkedQueue<>();
    final ConcurrentLinkedQueue<BufferedOutStream> recycleBuffers = new ConcurrentLinkedQueue<>();

    public Writer(State state, FileOutputStream out) throws Exception {
        this.state = state;

        // create and initialize FTID; we start in a world where every type is dropped unless it has been written
        this.FTID = new int[10 + state.classes.length + state.containers.length + state.enums.length];
        for (int i = 0; i < 10; i++)
            FTID[i] = i;

        // create FFID table
        {
            //next FFID, because 0 is always the string pool, even if not used later on
            int nextFFID = 1;

            // we do not keep a last FFID, so we have to create an array buffer first
            ArrayList<Integer> ids = new ArrayList<>();
            // string -> string
            ids.add(0);
            // containers
            for (ContainerType<?> c : state.containers) {
                ids.add(c.maxDeps == 0 ? -1 : nextFFID++);
            }
            // data fields
            for (Pool<?> p : state.classes) {
                for (FieldDeclaration<?, ?> f : p.dataFields) {
                    assert (f.id == ids.size());
                    ids.add(nextFFID++);
                }
            }
            // fix IDs
            FFID = new int[ids.size()];
            for (int i = 0; i < ids.size(); i++) {
                FFID[i] = ids.get(i);
            }
        }

        /**
         * *************** * G * ****************
         */
        if (null == state.guard || state.guard.isEmpty()) {
            out.i16((short) 0x2622);
        } else {
            out.i8((byte) '#');
            out.put(state.guard.getBytes());
            out.i8((byte) 0);
        }

        /**
         * *************** * S * ****************
         */

        // our string synchronisation barrier
        final Semaphore SB = state.strings.writeBlock(out);

        /**
         * *************** * T F * ****************
         */

        // write T and F to a buffer, while S is written
        final BufferedOutStream buffer = new BufferedOutStream();

        // @note here, the field data write tasks will be started already
        writeTF(buffer);
        SB.acquire();

        // write buffered TF-blocks
        out.write(buffer);
        recycleBuffers.add(buffer);

        /**
         * *************** * HD * ****************
         */

        // await data from all HD tasks
        while (true) {
            synchronized (this) {
                if (--awaitBuffers < 0)
                    break;
            }
            barrier.acquire();
            final BufferedOutStream buf = finishedBuffers.poll();
            if (null != buf) {
                out.writeSized(buf);
                recycleBuffers.add(buf);
            }
            // else: some buffer was discarded
        }

        out.close();

        // report errors
        if (null != writeErrors) {
            throw new OGSSException("write failed", writeErrors);
        }
    }

    /**
     * write T and F, start HD tasks and set awaitBuffers to the number of buffers if every entry had one block
     */
    private void writeTF(BufferedOutStream out) throws Exception {

        int awaitHulls = 0;
        final ArrayList<FieldDeclaration<?, ?>> fieldQueue;
        final StringPool string;

        /**
         * *************** * T Class * ****************
         */

        // calculate new bpos, sizes, object IDs and compress data arrays
        {
            final int[] bpos = new int[state.classes.length];
            int bases = 0;
            for (Pool<?> p : state.classes) {
                if (null == p.superPool) {
                    bases++;
                    State.pool.execute(new WCompress(this, p, bpos));
                }
            }

            // write count of the type block
            out.v64(state.classes.length);

            // initialize local state before waiting for compress
            fieldQueue = new ArrayList<>(2 * state.classes.length);
            string = state.strings;

            barrier.acquire(bases);

            // write types
            for (Pool<?> p : state.classes) {
                FTID[p.typeID] = nextFTID++;

                out.v64(string.IDs.get(p.name));
                out.v64(p.staticDataInstances);
                restrictions(p, out);
                if (null == p.superPool)
                    out.i8((byte) 0);
                else {
                    // superID
                    out.v64(p.superPool.typeID - 9);
                    // our bpo
                    out.v64(p.bpo);
                }

                out.v64(p.dataFields.size());

                // add field to queues for description and data tasks
                for (FieldDeclaration<?, ?> f : p.dataFields) {
                    fieldQueue.add(f);
                }
            }

        }

        /**
         * *************** * T Container * ****************
         */

        // write count of the type block
        {
            int count = 0;
            // set deps and calculate count
            for (HullType<?> c : state.containers) {
                if (c.maxDeps != 0) {
                    c.resetSerialization();
                    c.deps = c.maxDeps;
                    count++;
                }
            }
            if (string.maxDeps != 0) {
                awaitHulls = 1;
                string.deps = string.maxDeps;
            }
            awaitHulls += count;

            out.v64(count);
            for (HullType<?> c : state.containers) {
                if (c.maxDeps != 0) {
                    FTID[c.typeID] = nextFTID++;
                    if (c instanceof ArrayType<?>) {
                        ArrayType<?> t = (ArrayType<?>) c;
                        out.i8((byte) 0);
                        out.v64(FTID[t.base.typeID]);
                    } else if (c instanceof ListType<?>) {
                        ListType<?> t = (ListType<?>) c;
                        out.i8((byte) 1);
                        out.v64(FTID[t.base.typeID]);
                    } else if (c instanceof SetType<?>) {
                        SetType<?> t = (SetType<?>) c;
                        out.i8((byte) 2);
                        out.v64(FTID[t.base.typeID]);
                    } else if (c instanceof MapType<?, ?>) {
                        MapType<?, ?> t = (MapType<?, ?>) c;
                        out.i8((byte) 3);
                        out.v64(FTID[t.keyType.typeID]);
                        out.v64(FTID[t.valueType.typeID]);
                    }
                }
            }
        }

        // note: we cannot start field jobs immediately because they could decrement deps to 0 multiple times in that
        // case
        for (FieldDeclaration<?, ?> f : fieldQueue) {
            State.pool.execute(new WFT(this, f));
        }

        /**
         * *************** * T Enum * ****************
         */

        // write count of the type block
        out.v64(state.enums.length);
        for (EnumPool<?> p : state.enums) {
            FTID[p.typeID] = nextFTID++;
            out.v64(string.id(p.name));
            out.v64(p.values.length);
            for (EnumProxy<?> v : p.values) {
                out.v64(string.id(v.name));
            }
        }

        /**
         * *************** * F * ****************
         */

        for (FieldDeclaration<?, ?> f : fieldQueue) {
            // write info
            out.v64(string.id(f.name));
            out.v64(FTID[f.type.typeID]);
            restrictions(f, out);
        }

        out.close();

        // fields + hull types
        synchronized (this) {
            awaitBuffers += (fieldQueue.size() + awaitHulls);
        }
    }
}
