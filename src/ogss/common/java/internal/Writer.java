package ogss.common.java.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import ogss.common.java.api.SkillException;
import ogss.common.java.internal.fieldTypes.ArrayType;
import ogss.common.java.internal.fieldTypes.ListType;
import ogss.common.java.internal.fieldTypes.MapType;
import ogss.common.java.internal.fieldTypes.SetType;
import ogss.common.streams.BufferedOutStream;
import ogss.common.streams.FileOutputStream;
import ogss.common.streams.OutStream;

final public class Writer {

    protected final State state;

    /**
     * TODO serialization of restrictions
     */
    protected static final void restrictions(Pool<?> p, OutStream out) throws IOException {
        out.i8((byte) 0);
    }

    /**
     * TODO serialization of restrictions
     */
    protected static final void restrictions(FieldDeclaration<?, ?> f, OutStream out) throws IOException {
        out.i8((byte) 0);
    }

    // async reads will post their errors in this queue
    Throwable writeErrors = null;

    // our job synchronisation barrier
    final Semaphore barrier = new Semaphore(0, false);

    // @note can be used to add buffers concurrently to the write queue
    // @note the permit is given after we added a buffer; therefore the reader
    // can always read some buffer if he uses our permit (order is not
    // important)
    final ConcurrentLinkedQueue<BufferedOutStream> finishedBuffers = new ConcurrentLinkedQueue<>();
    final ConcurrentLinkedQueue<BufferedOutStream> recycleBuffers = new ConcurrentLinkedQueue<>();

    public Writer(State state, FileOutputStream out) throws Exception {
        this.state = state;

        /**
         * *************** * G * ****************
         */
        writeGuard(out);

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
        int awaitBuffers = writeTF(buffer);
        SB.acquire();

        // write buffered TF-blocks
        out.write(buffer);
        recycleBuffers.add(buffer);

        /**
         * *************** * HD * ****************
         */

        // await data from all HD tasks
        while (awaitBuffers != 0) {
            awaitBuffers--;
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
            throw new SkillException("write failed", writeErrors);
        }
    }

    private void writeGuard(FileOutputStream out) throws IOException {
        if (null == state.guard || state.guard.isEmpty()) {
            out.i16((short) 0x2622);
        } else {
            out.i8((byte) '#');
            out.put(state.guard.getBytes());
            out.i8((byte) 0);
        }
    }

    /**
     * write T and F, start HD tasks, and return the number of buffers to await
     */
    private int writeTF(BufferedOutStream out) throws Exception {

        int awaitHulls = 0;
        final ArrayList<FieldDeclaration<?, ?>> fieldQueue;
        final StringPool string;

        /**
         * *************** * T Class * ****************
         */

        // calculate new bpos, sizes, object IDs and compress data arrays
        {
            final int[] bpos = new int[state.classes.size()];
            int bases = 0;
            for (Pool<?> p : state.classes) {
                if (null == p.superPool) {
                    bases++;
                    State.pool.execute(new WCompress(this, p, bpos));
                }
            }

            // write count of the type block
            out.v64(state.classes.size());

            // initialize local state before waiting for compress
            fieldQueue = new ArrayList<>(2 * state.classes.size());
            string = state.strings;

            barrier.acquire(bases);

            // write types
            for (Pool<?> p : state.classes) {
                out.v64(string.id(p.name));
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
                    if (f.type instanceof HullType<?>) {
                        ((HullType<?>) f.type).deps++;
                        if (f.type instanceof StringPool) {
                            awaitHulls = 1;
                        }
                    }
                }
            }

        }

        /**
         * *************** * T Container * ****************
         */

        // write count of the type block
        {
            int count = state.containers.size();
            out.v64(count);
            for (HullType<?> c : state.containers) {
                if (c instanceof ArrayType<?>) {
                    ArrayType<?> t = (ArrayType<?>) c;
                    out.i8((byte) 0);
                    out.v64(t.base.typeID);
                    if (t.base instanceof HullType<?>) {
                        ((HullType<?>) t.base).deps++;
                        if (t.base instanceof StringPool) {
                            awaitHulls = 1;
                        }
                    }
                } else if (c instanceof ListType<?>) {
                    ListType<?> t = (ListType<?>) c;
                    out.i8((byte) 1);
                    out.v64(t.base.typeID);
                    if (t.base instanceof HullType<?>) {
                        ((HullType<?>) t.base).deps++;
                        if (t.base instanceof StringPool) {
                            awaitHulls = 1;
                        }
                    }
                } else if (c instanceof SetType<?>) {
                    SetType<?> t = (SetType<?>) c;
                    out.i8((byte) 2);
                    out.v64(t.base.typeID);
                    if (t.base instanceof HullType<?>) {
                        ((HullType<?>) t.base).deps++;
                        if (t.base instanceof StringPool) {
                            awaitHulls = 1;
                        }
                    }
                } else if (c instanceof MapType<?, ?>) {
                    MapType<?, ?> t = (MapType<?, ?>) c;
                    out.i8((byte) 3);
                    out.v64(t.keyType.typeID);
                    if (t.keyType instanceof HullType<?>) {
                        ((HullType<?>) t.keyType).deps++;
                        if (t.keyType instanceof StringPool) {
                            awaitHulls = 1;
                        }
                    }
                    out.v64(t.valueType.typeID);
                    if (t.valueType instanceof HullType<?>) {
                        ((HullType<?>) t.valueType).deps++;
                        if (t.valueType instanceof StringPool) {
                            awaitHulls = 1;
                        }
                    }
                }
            }
            awaitHulls += count;
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
        out.v64(0);
        // TODO enum write implementation

        /**
         * *************** * F * ****************
         */

        for (FieldDeclaration<?, ?> f : fieldQueue) {
            // write info
            out.v64(string.id(f.name));
            out.v64(f.type.typeID);
            restrictions(f, out);
        }

        out.close();

        // fields + hull types
        return fieldQueue.size() + awaitHulls;
    }
}
