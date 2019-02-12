package ogss.common.java.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import ogss.common.java.api.SkillException;
import ogss.common.java.internal.fieldTypes.ArrayType;
import ogss.common.java.internal.fieldTypes.ListType;
import ogss.common.java.internal.fieldTypes.MapType;
import ogss.common.java.internal.fieldTypes.SetType;
import ogss.common.java.internal.fieldTypes.SingleArgumentType;
import ogss.common.streams.BufferedOutStream;
import ogss.common.streams.FileOutputStream;
import ogss.common.streams.OutStream;

final public class Writer {

    protected final State state;

    /**
     * TODO serialization of restrictions
     */
    protected static final void restrictions(Pool<?, ?> p, OutStream out) throws IOException {
        out.i8((byte) 0);
    }

    /**
     * TODO serialization of restrictions
     */
    protected static final void restrictions(FieldDeclaration<?, ?> f, OutStream out) throws IOException {
        out.i8((byte) 0);
    }

    // async reads will post their errors in this queue
    SkillException writeErrors = null;

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
            // else, someone decided to discard his buffer
        }

        assert 0 == barrier.availablePermits() : ("something went wrong: " + barrier.availablePermits()
                + " permits remained unused");

        out.close();

        // report errors
        if (null != writeErrors) {
            throw writeErrors;
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

        // calculate new bpos, sizes, object IDs and compress data arrays
        {
            final int[] newBPOs = new int[state.classes.size()];
            int bases = 0;
            for (Pool<?, ?> p : state.classes) {
                if (p instanceof BasePool<?>) {
                    bases++;
                    State.pool.execute(new Runnable() {
                        @Override
                        public void run() {
                            ((BasePool<?>) p).compress(newBPOs);
                            barrier.release();
                        }
                    });
                }
            }
            barrier.acquire(bases);
        }

        /**
         * *************** * T Class * ****************
         */

        // write count of the type block
        out.v64(state.classes.size());

        // write types
        ArrayList<FieldDeclaration<?, ?>> fieldQueue = new ArrayList<>(2 * state.classes.size());
        int awaitHulls = 0;
        final IdentityHashMap<String, Integer> stringIDs = state.strings.IDs;
        for (Pool<?, ?> p : state.classes) {
            out.v64(stringIDs.get(p.name));
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
            State.pool.execute(new Task(f));
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
            out.v64(stringIDs.get(f.name));
            out.v64(f.type.typeID);
            restrictions(f, out);
        }

        // fields + hull types
        return fieldQueue.size() + awaitHulls;
    }

    /**
     * Data structure used for parallel serialization scheduling
     */
    final class Task implements Runnable {
        private final FieldDeclaration<?, ?> f;

        Task(FieldDeclaration<?, ?> f) {
            this.f = f;
        }

        @Override
        public void run() {
            BufferedOutStream buffer = recycleBuffers.poll();
            if (null == buffer) {
                buffer = new BufferedOutStream();
            } else {
                buffer.recycle();
            }

            boolean discard = true;
            Runnable tail = null;
            try {
                Pool<?, ?> owner = f.owner;
                int i = owner.bpo;

                buffer.v64(f.id);
                discard = f.write(i, i + owner.cachedSize, buffer);

                if (f.type instanceof HullType<?>) {
                    HullType<?> t = (HullType<?>) f.type;
                    synchronized (t) {
                        if (0 == --t.deps) {
                            // execute task in this thread to avoid unnecessary overhead
                            tail = new HullTask(t);
                        }
                    }
                }

            } catch (SkillException e) {
                synchronized (Writer.this) {
                    if (null == writeErrors)
                        writeErrors = e;
                    else
                        writeErrors.addSuppressed(e);
                }
            } catch (Throwable e) {
                synchronized (Writer.this) {
                    e = new SkillException("unexpected failure while writing field " + f.toString(), e);
                    if (null == writeErrors)
                        writeErrors = (SkillException) e;
                    else
                        writeErrors.addSuppressed(e);
                }
            } finally {
                // return the buffer in any case to ensure that there is a
                // buffer on error
                if (discard) {
                    recycleBuffers.add(buffer);
                } else {
                    finishedBuffers.add(buffer);
                }

                // ensure that writer can terminate, errors will be
                // printed to command line anyway, and we wont
                // be able to recover, because errors can only happen if
                // the skill implementation itself is
                // broken
                barrier.release();

                if (null != tail)
                    tail.run();
            }
        }
    }

    /**
     * Data structure used for parallel serialization scheduling
     */
    final class HullTask implements Runnable {
        private final HullType<?> t;

        HullTask(HullType<?> t) {
            this.t = t;
        }

        @Override
        public void run() {
            BufferedOutStream buffer = recycleBuffers.poll();
            if (null == buffer) {
                buffer = new BufferedOutStream();
            } else {
                buffer.recycle();
            }

            boolean discard = true;
            Runnable tail = null;
            try {
                buffer.v64(t.fieldID);
                discard = t.write(buffer);

                if (t instanceof SingleArgumentType<?, ?>) {
                    SingleArgumentType<?, ?> p = (SingleArgumentType<?, ?>) t;
                    if (p.base instanceof HullType<?>) {
                        HullType<?> t = (HullType<?>) p.base;
                        synchronized (t) {
                            if (0 == --t.deps) {
                                // execute task in this thread to avoid unnecessary overhead
                                tail = new HullTask(t);
                            }
                        }
                    }
                } else if (t instanceof MapType<?, ?>) {
                    MapType<?, ?> p = (MapType<?, ?>) t;
                    if (p.keyType instanceof HullType<?>) {
                        HullType<?> t = (HullType<?>) p.keyType;
                        synchronized (t) {
                            if (0 == --t.deps) {
                                // do not execute key hulls, as value hull is more likely and its execution would be
                                // blocked by the key hull
                                State.pool.execute(new HullTask(t));
                            }
                        }
                    }
                    if (p.valueType instanceof HullType<?>) {
                        HullType<?> t = (HullType<?>) p.valueType;
                        synchronized (t) {
                            if (0 == --t.deps) {
                                // execute task in this thread to avoid unnecessary overhead
                                tail = new HullTask(t);
                            }
                        }
                    }
                }
            } catch (SkillException e) {
                synchronized (Writer.this) {
                    if (null == writeErrors)
                        writeErrors = e;
                    else
                        writeErrors.addSuppressed(e);
                }
            } catch (Throwable e) {
                synchronized (Writer.this) {
                    e = new SkillException("unexpected failure while writing hull " + t.toString(), e);
                    if (null == writeErrors)
                        writeErrors = (SkillException) e;
                    else
                        writeErrors.addSuppressed(e);
                }
            } finally {
                // return the buffer in any case to ensure that there is a
                // buffer on error
                if (discard) {
                    recycleBuffers.add(buffer);
                } else {
                    finishedBuffers.add(buffer);
                }

                // ensure that writer can terminate, errors will be
                // printed to command line anyway, and we wont
                // be able to recover, because errors can only happen if
                // the skill implementation itself is
                // broken
                barrier.release();

                if (null != tail)
                    tail.run();
            }
        }
    }
}
